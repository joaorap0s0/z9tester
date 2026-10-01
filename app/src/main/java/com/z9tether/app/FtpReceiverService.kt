package com.z9tether.app

import android.app.*
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import java.io.*
import java.net.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class FtpReceiverService : Service() {
    companion object {
        const val ACTION_EVENT = "com.z9tether.app.EVENT"
        const val EXTRA_TYPE = "type"; const val EXTRA_NAME = "name"; const val EXTRA_COUNT = "count"; const val EXTRA_MESSAGE = "message"; const val EXTRA_IP = "ip"
        const val TYPE_STARTED = "started"; const val TYPE_STOPPED = "stopped"; const val TYPE_RECEIVED = "received"; const val TYPE_ERROR = "error"; const val TYPE_IP = "ip"
        const val CONTROL_PORT = 2121
        const val USER = "nikon"; const val PASS = "z9tether"
        private const val DATA_MIN = 32768; private const val DATA_MAX = 61000
    }
    private var running = false
    private var controlServer: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()
    private var count = 0
    @Volatile private var phoneIp = ""

    override fun onCreate() {
        super.onCreate(); createNotificationChannel(); startForeground(10, notification("Starting FTP receiver…")); running = true
        thread(name="z9-ftp-start") { startServer() }
    }

    private fun startServer() {
        phoneIp = NetworkUtils.localIpv4(this) ?: ""
        try {
            controlServer = ServerSocket(CONTROL_PORT)
            broadcast(TYPE_IP, ip=phoneIp)
            broadcast(TYPE_STARTED)
            updateNotification(if (phoneIp.isBlank()) "FTP ready, local IP not detected" else "FTP ready on $phoneIp:$CONTROL_PORT")
            while (running) { val s = controlServer!!.accept(); pool.execute { FtpSession(s).run() } }
        } catch (e: Exception) {
            if (running) { broadcast(TYPE_ERROR, message="FTP server failed: ${e.message ?: e.javaClass.simpleName}"); updateNotification("FTP server error") }
        }
    }

    inner class FtpSession(private val socket: Socket) {
        private var loggedIn=false; private var dataSocket:Socket?=null; private var passiveServer:ServerSocket?=null
        fun run() { socket.use { s ->
            s.soTimeout=120000; val input=BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8)); val out=BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8)); reply(out,"220 Z9 Tether FTP receiver ready")
            while(true){ val line=try{input.readLine()}catch(_:Exception){null}?:break; if(!handle(line,out)) break }; closeData()
        }}
        private fun handle(line:String,out:BufferedWriter):Boolean {
            val p=line.trim().split(Regex("\\s+"),limit=2); if(p.isEmpty()||p[0].isBlank()) return true; val cmd=p[0].uppercase(Locale.US); val arg=if(p.size>1)p[1].trim() else ""
            when(cmd){
                "USER"->{reply(out,if(arg==USER)"331 Password required" else "530 Invalid user")}
                "PASS"->{loggedIn=arg==PASS; reply(out,if(loggedIn)"230 Login successful" else "530 Login incorrect")}
                "SYST"->reply(out,"215 UNIX Type: L8"); "FEAT"->{reply(out,"211-Features");reply(out," UTF8");reply(out," PASV");reply(out," EPSV");reply(out,"211 End")}
                "OPTS","CLNT"->reply(out,"200 OK"); "PWD"->reply(out,"257 \"/\" is current directory"); "CWD","CDUP"->reply(out,"250 Directory changed")
                "TYPE","MODE","STRU"->reply(out,"200 OK"); "NOOP"->reply(out,"200 OK"); "PASV"->preparePassive(out,false); "EPSV"->preparePassive(out,true)
                "STOR"->{if(!loggedIn)reply(out,"530 Login required")else receiveFile(arg,out)}
                "QUIT"->{reply(out,"221 Goodbye");return false}; "ABOR"->{closeData();reply(out,"226 Transfer aborted")}
                else->reply(out,"502 Command not implemented")
            }; return true
        }
        private fun preparePassive(out:BufferedWriter,extended:Boolean){ closeData(); try{
            phoneIp=NetworkUtils.localIpv4(this@FtpReceiverService)?:phoneIp
            var port=0; var ss:ServerSocket?=null
            repeat(30){ if(ss==null) try{ val p=Random().nextInt(DATA_MAX-DATA_MIN+1)+DATA_MIN; ss=ServerSocket(p,1);port=p }catch(_:IOException){} }
            if(ss==null)throw IOException("No passive port available"); passiveServer=ss
            if(extended) reply(out,"229 Entering Extended Passive Mode (|||$port|)") else { val parts=(phoneIp.ifBlank{"127.0.0.1"}).split('.').map{it.toIntOrNull()?:127};reply(out,"227 Entering Passive Mode (${parts.joinToString(",")},${port/256},${port%256})") }
        }catch(e:Exception){reply(out,"425 Can't open passive connection")}}
        private fun receiveFile(remoteName:String,out:BufferedWriter){val server=passiveServer?:run{reply(out,"425 Use PASV first");return};reply(out,"150 Opening binary data connection");var uri:Uri?=null
            try{dataSocket=server.accept();server.close();passiveServer=null;val filename=remoteName.substringAfterLast('/').ifBlank{"DSC_TEST.JPG"};uri=createMediaItem(filename);contentResolver.openOutputStream(uri!!,"w")!!.use{dest->dataSocket!!.getInputStream().use{src->src.copyTo(dest,1024*1024)}};finishMediaItem(uri!!);count++;sendBroadcast(Intent(ACTION_EVENT).putExtra(EXTRA_TYPE,TYPE_RECEIVED).putExtra(EXTRA_NAME,filename).putExtra(EXTRA_COUNT,count));updateNotification("Received $count photo${if(count==1)""else"s"}");reply(out,"226 Transfer complete")}
            catch(e:Exception){uri?.let{try{contentResolver.delete(it,null,null)}catch(_:Exception){}};reply(out,"451 Requested action aborted: local error")}
            finally{closeData()}}
        private fun reply(out:BufferedWriter,text:String){out.write(text);out.write("\r\n");out.flush()}
        private fun closeData(){try{dataSocket?.close()}catch(_:Exception){};dataSocket=null;try{passiveServer?.close()}catch(_:Exception){};passiveServer=null}
    }
    private fun createMediaItem(original:String):Uri{val ext=original.substringAfterLast('.', "jpg").lowercase(Locale.US);val mime=when(ext){"jpg","jpeg"->"image/jpeg";"heic","heif"->"image/heic";"nef"->"image/x-nikon-nef";"png"->"image/png";else->"application/octet-stream"};val safe=original.replace(Regex("[^A-Za-z0-9._-]"),"_");val stamp=SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(Date());val v=ContentValues().apply{put(MediaStore.Images.Media.DISPLAY_NAME,"Z9_${stamp}_$safe");put(MediaStore.Images.Media.MIME_TYPE,mime);if(Build.VERSION.SDK_INT>=29){put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Z9 Tether");put(MediaStore.Images.Media.IS_PENDING,1)}};return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v)?:throw IOException("Unable to create media file")}
    private fun finishMediaItem(uri:Uri){if(Build.VERSION.SDK_INT>=29)contentResolver.update(uri,ContentValues().apply{put(MediaStore.Images.Media.IS_PENDING,0)},null,null)}
    override fun onDestroy(){running=false;try{controlServer?.close()}catch(_:Exception){};pool.shutdownNow();broadcast(TYPE_STOPPED);super.onDestroy()}
    override fun onBind(intent:Intent?):IBinder?=null
    private fun broadcast(type:String,message:String?=null,ip:String?=null){val i=Intent(ACTION_EVENT).putExtra(EXTRA_TYPE,type).putExtra(EXTRA_COUNT,count);if(message!=null)i.putExtra(EXTRA_MESSAGE,message);if(ip!=null)i.putExtra(EXTRA_IP,ip);sendBroadcast(i)}
    private fun createNotificationChannel(){getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("z9tether","Z9 Tether",NotificationManager.IMPORTANCE_LOW))}
    private fun notification(text:String)=Notification.Builder(this,"z9tether").setContentTitle("Z9 Tether").setContentText(text).setSmallIcon(android.R.drawable.ic_menu_upload).setOngoing(true).build()
    private fun updateNotification(text:String)=getSystemService(NotificationManager::class.java).notify(10,notification(text))
}
