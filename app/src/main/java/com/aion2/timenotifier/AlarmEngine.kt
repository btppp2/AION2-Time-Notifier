package com.aion2.timenotifier
import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.time.ZonedDateTime

object AlarmEngine {
 const val CHANNEL="aion2_events_v1"
 val offsets=listOf(30,10,5,0)
 fun prefs(ctx:Context)=ctx.getSharedPreferences("preferences",Context.MODE_PRIVATE)
 fun group(ctx:Context)=prefs(ctx).getInt("group",1)
 fun enabled(ctx:Context,title:String)=prefs(ctx).getBoolean("enabled_$title",true)
 fun offsetEnabled(ctx:Context,offset:Int)=prefs(ctx).getBoolean("offset_$offset",offset==10||offset==0)
 fun groupEnabled(ctx:Context,g:Int)=prefs(ctx).getBoolean("group_enabled_$g",g==group(ctx))
 fun visible(ctx:Context)=Schedule.events.filter{(if(it.group==0)(1..3).any{g->groupEnabled(ctx,g)} else groupEnabled(ctx,it.group))&&enabled(ctx,it.title)}
 fun shouldNotify(ctx:Context,event:Event)=enabled(ctx,event.title)&&(if(event.group==0)(1..3).any{groupEnabled(ctx,it)} else groupEnabled(ctx,event.group))
 fun createChannel(ctx:Context) {
  ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"콘텐츠 시작 알림",NotificationManager.IMPORTANCE_HIGH))
 }
 private fun requestCode(event:Event,offset:Int)=event.id.hashCode()*31+offset
 private fun intent(ctx:Context,event:Event,offset:Int):PendingIntent {
  val i=Intent(ctx,AlarmReceiver::class.java).putExtra("event",event.id).putExtra("offset",offset)
  return PendingIntent.getBroadcast(ctx,requestCode(event,offset),i,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 }
 fun exactAllowed(ctx:Context)=Build.VERSION.SDK_INT<31||ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
 fun reschedule(ctx:Context) {
  createChannel(ctx)
  val am=ctx.getSystemService(AlarmManager::class.java)
  for(event in Schedule.events) for(offset in offsets) am.cancel(intent(ctx,event,offset))
  if(!exactAllowed(ctx)) return
  val now=ZonedDateTime.now(Schedule.zone)
  for(event in visible(ctx)) for(offset in offsets.filter{offsetEnabled(ctx,it)}) {
   val upcoming=Schedule.next(event,now.plusMinutes(offset.toLong()))
   val millis=upcoming.minusMinutes(offset.toLong()).toInstant().toEpochMilli()
   try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,millis,intent(ctx,event,offset)) }
   catch(_:SecurityException){return}
  }
 }
 fun notifyEvent(ctx:Context,event:Event,offset:Int) {
  createChannel(ctx)
  if(Build.VERSION.SDK_INT>=33&&ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
  val launch=PendingIntent.getActivity(ctx,0,Intent(ctx,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val notification=Notification.Builder(ctx,CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
   .setContentTitle(event.title+(if(event.group>0)" · ${event.group}그룹" else ""))
   .setContentText(if(offset==0)"지금 시작합니다!" else "${offset}분 후 시작합니다.")
   .setAutoCancel(true).setContentIntent(launch).setCategory(Notification.CATEGORY_REMINDER).build()
  ctx.getSystemService(NotificationManager::class.java).notify(requestCode(event,offset),notification)
  speakIfAudible(ctx,event,offset)
 }
 private var speaker:TextToSpeech?=null
 fun speakIfAudible(ctx:Context,event:Event,offset:Int){
  val audio=ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  if(audio.ringerMode!=AudioManager.RINGER_MODE_NORMAL)return
  if(audio.getStreamVolume(AudioManager.STREAM_MUSIC)==0)return
  val phrase=event.title+(if(offset==0)" 지금 시작합니다." else " ${offset}분 후 시작합니다.")
  speaker?.stop();speaker?.shutdown();speaker=null
  val app=ctx.applicationContext
  speaker=TextToSpeech(app){status->
   if(status==TextToSpeech.SUCCESS){
    speaker?.language=Locale.KOREAN
    speaker?.speak(phrase,TextToSpeech.QUEUE_FLUSH,null,"aion2_${event.id}_$offset")
   }
  }
 }
}
class AlarmReceiver:BroadcastReceiver(){
 override fun onReceive(context:Context,intent:Intent){
  val event=Schedule.events.find{it.id==intent.getStringExtra("event")}?:return
  val offset=intent.getIntExtra("offset",-1)
  if(!AlarmEngine.shouldNotify(context,event)||!AlarmEngine.offsetEnabled(context,offset))return
  AlarmEngine.notifyEvent(context,event,offset)
  AlarmEngine.reschedule(context)
 }
}
class RestoreReceiver:BroadcastReceiver(){
 override fun onReceive(context:Context,intent:Intent){AlarmEngine.reschedule(context)}
}
