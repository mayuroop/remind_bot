package com.example.reminderbot;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public class BotMain extends TelegramLongPollingBot {
    static class R {long c;String m;Instant t;R(long c,String m,Instant t){this.c=c;this.m=m;this.t=t;}}
    PriorityQueue<R> q=new PriorityQueue<>(Comparator.comparing(r->r.t));
    ScheduledExecutorService s=Executors.newScheduledThreadPool(5);
    int id=0;

    public static void main(String[] a) throws Exception {
        new TelegramBotsApi(DefaultBotSession.class).registerBot(new BotMain());
        Thread.currentThread().join();
    }

    public String getBotUsername(){return "reminder46bot";}
    public String getBotToken(){return "8085889854:AAH-0yp_S91pMRHhxk4-GOf3nGCk8U0vHfI";}

    public void onUpdateReceived(Update u){
        if(!u.hasMessage()||!u.getMessage().hasText())return;
        long c=u.getMessage().getChatId();
        String t=u.getMessage().getText();
        try{
            if(t.startsWith("/start")||t.startsWith("/help"))send(c,"📅 /remind 2025-10-28 14:00 msg\n⏱️ /remind in 10m msg\n📋 /list\n❌ /cancel <id>");
            else if(t.startsWith("/list")){
                if(q.isEmpty())send(c,"📭 No reminders");
                else{StringBuilder b=new StringBuilder("📋 Reminders:\n");int i=0;for(R r:q)b.append(++i+". "+r.m+" @ "+fmt(r.t)+"\n");send(c,b.toString());}
            }
            else if(t.startsWith("/cancel")){
                try{int n=Integer.parseInt(t.split(" ")[1]);List<R>l=new ArrayList<>(q);q.remove(l.get(n-1));send(c,"✅ Cancelled #"+n);}catch(Exception e){send(c,"❌ Invalid");}
            }
            else if(t.startsWith("/remind")){
                Matcher m=Pattern.compile("^/remind\\s+in\\s+((?:\\d+[smhd])+)\\s+(.+)$",2).matcher(t);
                if(m.matches()){long sec=parse(m.group(1));add(c,m.group(2),Instant.now().plusSeconds(sec));return;}
                m=Pattern.compile("^/remind\\s+(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{2}:\\d{2})\\s+(.+)$",2).matcher(t);
                if(m.matches()){add(c,m.group(3),LocalDateTime.parse(m.group(1)+" "+m.group(2),DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")).atZone(ZoneId.of("Asia/Kolkata")).toInstant());return;}
                send(c,"❌ Use: /remind in 10m msg OR /remind 2025-10-28 14:00 msg");
            }
        }catch(Exception e){send(c,"❌ "+e.getMessage());}
    }

    void add(long c,String m,Instant t){
        if(t.isBefore(Instant.now()))throw new IllegalArgumentException("Time must be future");
        R r=new R(c,m,t);q.add(r);
        s.schedule(()->send(r.c,"⏰ "+r.m),Duration.between(Instant.now(),t).getSeconds(),TimeUnit.SECONDS);
        send(c,"✅ Reminder #"+(++id)+" @ "+fmt(t));
    }

    long parse(String d){
        Matcher m=Pattern.compile("(\\d+)([smhd])").matcher(d.toLowerCase());
        long sec=0;
        while(m.find())sec+=Integer.parseInt(m.group(1))*(m.group(2).equals("s")?1:m.group(2).equals("m")?60:m.group(2).equals("h")?3600:86400);
        return sec;
    }

    String fmt(Instant i){return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Kolkata")).format(i);}

    void send(long c,String t){try{execute(SendMessage.builder().chatId(c).text(t).build());}catch(Exception e){}}
}
