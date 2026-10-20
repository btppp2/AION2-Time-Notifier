package com.aion2.timenotifier
import java.time.*
data class Event(val id:String,val title:String,val days:Set<Int>,val hour:Int,val minute:Int,val group:Int=0)
object Schedule {
 val zone=ZoneId.of("Asia/Seoul")
 val events:List<Event> = buildList {
  for(h in listOf(1,5,9,13,17,21)) add(Event("kaira_$h","카이라",(1..7).toSet(),h,0))
  for(h in listOf(20,23)) add(Event("riftbattle_$h","시공쟁탈전",setOf(1,4,6),h,0))
  add(Event("rift","균열지대",setOf(2,4),22,0))
  for(g in 1..3) {
   val offset=(g-1)*30
   add(Event("arti_$g","아티쟁",setOf(3,6),21+(20+offset)/60,(20+offset)%60,g))
   add(Event("abyss_$g","어비스 보스",setOf(3,6),21+(45+offset)/60,(45+offset)%60,g))
   add(Event("nahma_$g","나흐마",setOf(5,7),22+offset/60,offset%60,g))
  }
 }
 val titles=listOf("카이라","시공쟁탈전","균열지대","아티쟁","어비스 보스","나흐마")
 fun next(event:Event,now:ZonedDateTime=ZonedDateTime.now(zone)):ZonedDateTime {
  for(days in 0..7) {
   val date=now.toLocalDate().plusDays(days.toLong())
   if(date.dayOfWeek.value !in event.days) continue
   val candidate=date.atTime(event.hour,event.minute).atZone(zone)
   if(candidate.isAfter(now)) return candidate
  }
  error("No next event")
 }
 fun display(event:Event)="%02d:%02d".format(event.hour,event.minute)
 fun weekday(event:Event)=event.days.sorted().joinToString("·"){listOf("월","화","수","목","금","토","일")[it-1]}
}
