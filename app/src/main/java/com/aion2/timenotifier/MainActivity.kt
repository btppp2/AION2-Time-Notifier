package com.aion2.timenotifier
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.provider.Settings
import android.net.Uri
import android.widget.*
import java.time.*
import java.time.format.DateTimeFormatter

class MainActivity:Activity(){
 private lateinit var root:LinearLayout
 private lateinit var countdown:TextView
 private lateinit var scheduleList:LinearLayout
 private val handler=Handler(Looper.getMainLooper())
 private val ticker=object:Runnable{override fun run(){if(::countdown.isInitialized)updateCountdown();handler.postDelayed(this,1000)}}
 private val navy=Color.rgb(12,23,43)
 private val light=Color.rgb(225,237,255)
 private val cyan=Color.rgb(86,213,235)
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);AlarmEngine.reschedule(this);buildUi();requestNotificationPermission()}
 override fun onResume(){super.onResume();handler.removeCallbacks(ticker);handler.post(ticker)}
 override fun onPause(){handler.removeCallbacks(ticker);super.onPause()}
 private fun label(text:String,size:Float=16f,color:Int=light)=TextView(this).apply{this.text=text;textSize=size;setTextColor(color);setPadding(0,8,0,8)}
 private fun section(text:String){root.addView(label(text,19f,cyan))}
 private fun buildUi(){
  val scroll=ScrollView(this).apply{setBackgroundColor(navy);fillViewport=true}
  root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,28,24,40)}
  scroll.addView(root);setContentView(scroll)
  root.addView(label("AION2 타임 알리미",27f,cyan))
  root.addView(label("한국시간(KST) · 매주 자동 반복",13f))
  section("다음 콘텐츠")
  countdown=label("계산 중...",21f);root.addView(countdown)
  section("내 그룹")
  val groups=RadioGroup(this).apply{orientation=RadioGroup.HORIZONTAL}
  for(g in 1..3)groups.addView(RadioButton(this).apply{id=100+g;text="${g}그룹";setTextColor(light)})
  groups.check(100+AlarmEngine.group(this))
  groups.setOnCheckedChangeListener{_,checked->if(checked in 101..103){
   AlarmEngine.prefs(this).edit().putInt("group",checked-100).apply()
   AlarmEngine.reschedule(this);updateCountdown();buildScheduleList()
  }}
  root.addView(groups)
  section("콘텐츠 알림")
  for(title in Schedule.titles)root.addView(CheckBox(this).apply{
   text=title;setTextColor(light);isChecked=AlarmEngine.enabled(this@MainActivity,title)
   setOnCheckedChangeListener{_,checked->
    AlarmEngine.prefs(this@MainActivity).edit().putBoolean("enabled_$title",checked).apply()
    AlarmEngine.reschedule(this@MainActivity);updateCountdown()
   }
  })
  section("언제 알릴까요?")
  for(minutes in AlarmEngine.offsets)root.addView(CheckBox(this).apply{
   text=if(minutes==0)"시작 정각" else "${minutes}분 전"
   setTextColor(light);isChecked=AlarmEngine.offsetEnabled(this@MainActivity,minutes)
   setOnCheckedChangeListener{_,checked->
    AlarmEngine.prefs(this@MainActivity).edit().putBoolean("offset_$minutes",checked).apply()
    AlarmEngine.reschedule(this@MainActivity)
   }
  })
  section("알림 권한")
  root.addView(Button(this).apply{
   text="정확한 알람 권한 설정"
   setOnClickListener{
    if(Build.VERSION.SDK_INT>=31)startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:$packageName")))
    else Toast.makeText(this@MainActivity,"이미 지원되는 Android 버전입니다",Toast.LENGTH_SHORT).show()
   }
  })
  root.addView(Button(this).apply{
   text="알림 테스트"
   setOnClickListener{AlarmEngine.notifyEvent(this@MainActivity,Schedule.events.first(),5)}
  })
  section("선택 그룹 주간 시간표")
  scheduleList=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  root.addView(scheduleList);buildScheduleList()
  root.addView(label("※ 게임 패치 시 시간표 업데이트가 필요합니다.",12f))
  updateCountdown()
 }
 private fun buildScheduleList(){
  if(!::scheduleList.isInitialized)return
  scheduleList.removeAllViews()
  for(e in Schedule.events.filter{it.group==0||it.group==AlarmEngine.group(this)}
   .sortedWith(compareBy({it.days.min()},{it.hour},{it.minute}))){
   scheduleList.addView(label("${Schedule.weekday(e)}  ${Schedule.display(e)}  ${e.title}",14f))
  }
 }
 private fun updateCountdown(){
  val now=ZonedDateTime.now(Schedule.zone)
  val next=AlarmEngine.visible(this).map{it to Schedule.next(it,now)}.minByOrNull{it.second.toInstant()}
  countdown.text=if(next==null)"활성화된 콘텐츠가 없습니다" else {
   val remaining=Duration.between(now,next.second).seconds.coerceAtLeast(0)
   val days=remaining/86400;val hours=(remaining%86400)/3600
   val mins=(remaining%3600)/60;val secs=remaining%60
   "${next.first.title}${if(next.first.group>0)" · ${next.first.group}그룹" else ""}\n"+
   "${next.second.format(DateTimeFormatter.ofPattern("E HH:mm"))} KST\n"+
   "${if(days>0)"${days}일 " else ""}%02d:%02d:%02d 남음".format(hours,mins,secs)
  }
 }
 private fun requestNotificationPermission(){
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
   requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),1)
 }
}
