<?php
declare(strict_types=1);
/**
 * Correos de AERIQ con la imagen corporativa (T32, T35-T37).
 * Cada correo sale en dos versiones: texto plano y HTML hecho con tablas y estilos en línea, que es lo que
 * Gmail y Outlook (escritorio y móvil) muestran igual. El logo es un PNG publicado en la propia web: los
 * clientes de correo no muestran SVG ni imágenes incrustadas en base64 de forma fiable.
 */

const AERIQ_MAIL_GREEN='#0e6b5e';
const AERIQ_MAIL_INK='#17201d';
const AERIQ_MAIL_MUTED='#5e6b66';

/**
 * Dirección pública de la web (con la barra final), para los enlaces y el logo de los correos.
 * Orden: variable de entorno AERIQ_PUBLIC_URL, MailConfig.php ['public_url'] y, si no hay ninguna,
 * la carpeta del script en el host configurado del servidor (SERVER_NAME, no HTTP_HOST, que lo elige el cliente).
 */
/**
 * Diseño lógico: (string $path='') → aeriq_public_url() → string; según doc/aeriq_business_logic_design.md.
 */
function aeriq_public_url(string $path=''): string {
    $base=trim((string)(getenv('AERIQ_PUBLIC_URL')?:''));
    if ($base==='' && is_file(__DIR__.'/MailConfig.php')) {
        $opts=require __DIR__.'/MailConfig.php';
        if (is_array($opts)) $base=trim((string)($opts['public_url']??''));
    }
    if ($base==='') {
        $host=strtolower((string)($_SERVER['SERVER_NAME']??'localhost'));
        if (!preg_match('/^[a-z0-9.-]+$/D',$host)) $host='localhost';
        $port=(int)($_SERVER['SERVER_PORT']??0);
        $https=(!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS']!=='off') || $port===443;
        $authority=$host.(($port && $port!==80 && $port!==443)?':'.$port:'');
        $dir=str_replace('\\','/',dirname((string)($_SERVER['SCRIPT_NAME']??'/')));
        $base=($https?'https':'http').'://'.$authority.rtrim($dir,'/').'/';
    }
    return rtrim($base,'/').'/'.ltrim($path,'/');
}

/**
 * Gestiona mail escape.
 * Diseño lógico: (string $text) → aeriq_mail_escape() → string; según doc/aeriq_business_logic_design.md.
 */
function aeriq_mail_escape(string $text): string { return htmlspecialchars($text,ENT_QUOTES|ENT_SUBSTITUTE,'UTF-8'); }

/**
 * Compone un correo corporativo. $blocks es una lista de bloques:
 *   ['p', 'texto']                     párrafo
 *   ['code', '123456']                 código destacado
 *   ['button', 'Texto', 'https://…']   botón (y, debajo, el enlace en claro por si el botón no se ve)
 *   ['facts', ['Etiqueta'=>'valor']]   tabla de datos (resumen de una solicitud)
 *   ['note', 'texto']                  aviso pequeño
 * Devuelve ['text'=>…, 'html'=>…].
 */
/**
 * Diseño lógico: (string $title,array $blocks,string $preheader='') → aeriq_mail_compose() → array; según doc/aeriq_business_logic_design.md.
 */
function aeriq_mail_compose(string $title,array $blocks,string $preheader=''): array {
    $e='aeriq_mail_escape';$green=AERIQ_MAIL_GREEN;$ink=AERIQ_MAIL_INK;$muted=AERIQ_MAIL_MUTED;
    $text=[$title,str_repeat('=',min(60,max(10,mb_strlen($title)))),''];
    $rows='';
    foreach ($blocks as $b) {
        switch ($b[0]) {
            case 'p':
                $text[]=$b[1];$text[]='';
                $rows.='<tr><td style="padding:0 0 16px;font:16px/1.55 Arial,Helvetica,sans-serif;color:'.$ink.'">'.nl2br($e($b[1])).'</td></tr>';
                break;
            case 'code':
                $text[]='Código: '.$b[1];$text[]='';
                $rows.='<tr><td style="padding:4px 0 20px"><table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr><td align="center" style="background:#eef6f2;border-radius:12px;padding:18px;font:bold 34px/1 \'Courier New\',monospace;letter-spacing:8px;color:'.$green.'">'.$e($b[1]).'</td></tr></table></td></tr>';
                break;
            case 'button':
                $text[]=$b[1].': '.$b[2];$text[]='';
                // Botón «a prueba de Outlook»: celda con fondo + enlace con relleno; Outlook ignora el radio, el resto lo respeta.
                $rows.='<tr><td style="padding:4px 0 8px"><table role="presentation" cellpadding="0" cellspacing="0"><tr><td bgcolor="'.$green.'" style="border-radius:26px;background:'.$green.'">'
                    .'<a href="'.$e($b[2]).'" target="_blank" style="display:inline-block;padding:14px 28px;font:bold 16px/1.2 Arial,Helvetica,sans-serif;color:#ffffff;text-decoration:none;border-radius:26px">'.$e($b[1]).'</a>'
                    .'</td></tr></table></td></tr>'
                    .'<tr><td style="padding:0 0 18px;font:12px/1.5 Arial,Helvetica,sans-serif;color:'.$muted.'">Si el botón no funciona, copia este enlace en el navegador:<br><a href="'.$e($b[2]).'" style="color:'.$green.';word-break:break-all">'.$e($b[2]).'</a></td></tr>';
                break;
            case 'facts':
                $cells='';
                foreach ($b[1] as $label=>$value) {
                    if ($value===null || $value==='') continue;
                    $text[]=$label.': '.$value;
                    $cells.='<tr><td valign="top" style="padding:8px 12px 8px 0;border-bottom:1px solid #e3eae7;font:bold 13px/1.4 Arial,Helvetica,sans-serif;color:'.$muted.';white-space:nowrap">'.$e((string)$label).'</td>'
                        .'<td valign="top" style="padding:8px 0;border-bottom:1px solid #e3eae7;font:14px/1.45 Arial,Helvetica,sans-serif;color:'.$ink.'">'.nl2br($e((string)$value)).'</td></tr>';
                }
                $text[]='';
                $rows.='<tr><td style="padding:0 0 20px"><table role="presentation" width="100%" cellpadding="0" cellspacing="0">'.$cells.'</table></td></tr>';
                break;
            case 'note':
                $text[]=$b[1];$text[]='';
                $rows.='<tr><td style="padding:0 0 12px;font:13px/1.5 Arial,Helvetica,sans-serif;color:'.$muted.'">'.nl2br($e($b[1])).'</td></tr>';
                break;
        }
    }
    $site=aeriq_public_url();
    $text[]='--';$text[]='AERIQ · Código Norte';$text[]='Medición colaborativa de ozono · '.$site;
    $text[]='Este correo se ha enviado automáticamente. Si no esperabas recibirlo, puedes ignorarlo.';
    $logo=aeriq_public_url('assets/aeriq_logo.png');
    $html='<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">'
        .'<meta name="color-scheme" content="light only"><title>'.$e($title).'</title></head>'
        .'<body style="margin:0;padding:0;background:#f4f7f6;-webkit-text-size-adjust:100%">'
        .'<div style="display:none;max-height:0;overflow:hidden;opacity:0">'.$e($preheader!==''?$preheader:$title).'</div>'
        .'<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#f4f7f6"><tr><td align="center" style="padding:28px 12px">'
        .'<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:560px">'
        // Cabecera: franja verde corporativa con el logo sobre blanco.
        .'<tr><td style="background:'.$green.';height:6px;line-height:6px;font-size:0;border-radius:18px 18px 0 0">&nbsp;</td></tr>'
        .'<tr><td style="background:#ffffff;padding:24px 32px 8px;border-left:1px solid #e3eae7;border-right:1px solid #e3eae7">'
        .'<a href="'.$e($site).'" target="_blank"><img src="'.$e($logo).'" width="132" height="41" alt="AERIQ" style="display:block;border:0;width:132px;height:41px"></a></td></tr>'
        .'<tr><td style="background:#ffffff;padding:16px 32px 12px;border-left:1px solid #e3eae7;border-right:1px solid #e3eae7">'
        .'<h1 style="margin:0 0 16px;font:bold 24px/1.3 Arial,Helvetica,sans-serif;color:'.$ink.'">'.$e($title).'</h1>'
        .'<table role="presentation" width="100%" cellpadding="0" cellspacing="0">'.$rows.'</table></td></tr>'
        // Pie corporativo.
        .'<tr><td style="background:#1c2522;padding:20px 32px;border-radius:0 0 18px 18px">'
        .'<p style="margin:0 0 4px;font:bold 14px/1.4 Arial,Helvetica,sans-serif;color:#ffffff">AERIQ · Código Norte</p>'
        .'<p style="margin:0 0 10px;font:13px/1.5 Arial,Helvetica,sans-serif;color:#c9d6d1">Medición colaborativa de ozono · <a href="'.$e($site).'" style="color:#8fd3bf">'.$e(preg_replace('#^https?://#','',rtrim($site,'/'))).'</a></p>'
        .'<p style="margin:0;font:12px/1.5 Arial,Helvetica,sans-serif;color:#9aaaa4">Este correo se ha enviado automáticamente. Si no esperabas recibirlo, puedes ignorarlo.</p>'
        .'</td></tr></table></td></tr></table></body></html>';
    return ['text'=>implode("\n",$text),'html'=>$html];
}
