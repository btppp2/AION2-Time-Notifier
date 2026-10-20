package com.aion2.timenotifier
import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import java.time.ZonedDateTime

object AlarmEngine {
 const val CHANNEL="aion2_events_v1"
 val offsets=listOf(30,10,5,0)
 fun prefs(ctx:Context)=ctx.getSharedPreferences("preferences",Context.MODE_PRIVATE)
 fun group(ctx:Context)=prefs(ctx).getInt("group",1)
 fun enabled(ctx:Context,title:String)=prefs(ctx).getBoolean("enabled_$title",true)
 fun offsetEnabled(ctx:Context,offset:Int)=prefs(ctx).getBoolean("offset_$offset",offset==10||offset==0)
 fun masterEnabled(ctx:Context)=prefs(ctx).getBoolean("master_enabled",true)
 fun visible(ctx:Context)=Schedule.events.filter{masterEnabled(ctx)&&(it.group==0||it.group==group(ctx))&&enabled(ctx,it.title)}
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
 }
}
class AlarmReceiver:BroadcastReceiver(){
 override fun onReceive(context:Context,intent:Intent){
  val event=Schedule.events.find{it.id==intent.getStringExtra("event")}?:return
  val offset=intent.getIntExtra("offset",-1)
  if(event.group!=0&&event.group!=AlarmEngine.group(context))return
  if(!AlarmEngine.enabled(context,event.title)||!AlarmEngine.offsetEnabled(context,offset))return
  AlarmEngine.notifyEvent(context,event,offset)
  AlarmEngine.reschedule(context)
 }
}
class RestoreReceiver:BroadcastReceiver(){
 override fun onReceive(context:Context,intent:Intent){AlarmEngine.reschedule(context)}
}
