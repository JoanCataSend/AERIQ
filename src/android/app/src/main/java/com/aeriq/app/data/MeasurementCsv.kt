package com.aeriq.app.data

/** CSV con texto escapado, fórmulas neutralizadas y coordenadas numéricas intactas. */
fun measurementCsv(rows: List<StoredReading>): String {
    fun cell(value: Any?): String {
        val raw=value?.toString() ?: ""
        val safe=if(value is String && raw.trimStart().firstOrNull() in listOf('=','+','-','@')) "'$raw" else raw
        return "\""+safe.replace("\"","\"\"")+"\""
    }
    return "\uFEFFfecha_utc;valor;unidad;dispositivo;campana;zona;latitud;longitud;simulado\r\n"+
        rows.joinToString("\r\n") { r->listOf(r.date,r.value,r.unit,r.device,r.campaign,r.zone,r.latitude,r.longitude,r.simulated).joinToString(";"){cell(it)} }
}
