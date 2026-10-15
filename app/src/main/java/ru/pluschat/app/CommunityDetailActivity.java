package ru.pluschat.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class CommunityDetailActivity extends Activity {
    private String id="", titleText="";
    private boolean channel;
    private LinearLayout list;
    private final int bg=Color.rgb(10,12,20), panel=Color.rgb(25,29,43), white=Color.rgb(246,247,255), muted=Color.rgb(157,166,190), accent=Color.rgb(170,153,255);
    @Override public void onCreate(Bundle state){super.onCreate(state);id=getIntent().getStringExtra("community_id");titleText=getIntent().getStringExtra("community_title");channel=getIntent().getBooleanExtra("channel",false);render();}
    private GradientDrawable shape(int c){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(18);return d;}
    private TextView label(String s,int size,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextColor(bold?white:muted);t.setTextSize(size);if(bold)t.setTypeface(null,android.graphics.Typeface.BOLD);return t;}
    private Button button(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(bg);b.setBackground(shape(accent));b.setOnClickListener(v->r.run());return b;}
    private EditText field(String hint){EditText e=new EditText(this);e.setHint(hint);e.setTextColor(white);e.setHintTextColor(muted);e.setTextSize(15);e.setPadding(14,10,14,10);e.setBackground(shape(panel));return e;}
    private void render(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(20,24,20,20);root.setBackgroundColor(bg);
        root.addView(label(titleText==null?"Сообщество":titleText,25,true));
        root.addView(label(channel?"Канал · публикации и подписчики":"Группа · участники, темы и права",14,false));
        root.addView(button(channel?"Создать публикацию":"Создать тему",()->createContent()));
        root.addView(button(channel?"Настройки канала":"Настройки группы",()->editSettings()));
        root.addView(button(channel?"Подписчики":"Участники",()->loadSublist()));
        if(!channel)root.addView(button("Пригласить участника по ID",()->inviteMember()));
        root.addView(button("Обновить",this::loadContent));
        ScrollView scroll=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(button("Назад",this::finish));setContentView(root);loadContent();
    }
    private void loadContent(){
        list.removeAllViews();
        String endpoint=(channel?"channels/":"groups/")+id+(channel?"/posts":"/topics");
        ApiClient.request("GET",endpoint,null,SessionStore.token(this),(data,error)->{
            if(error!=null){list.addView(label(error,14,false));return;}
            JSONArray arr=data.optJSONArray(channel?"posts":"topics");if(arr==null)arr=data.optJSONArray("data");
            if(arr==null||arr.length()==0){list.addView(label(channel?"Публикаций пока нет.":"Тем пока нет. Создай первую.",14,false));return;}
            for(int i=0;i<arr.length();i++){JSONObject item=arr.optJSONObject(i);if(item==null)continue;
                String name=item.optString("title",item.optString("body",channel?"Публикация":"Тема"));
                LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(16,14,16,14);card.setBackground(shape(panel));LinearLayout.LayoutParams cardParams=new LinearLayout.LayoutParams(-1,-2);cardParams.setMargins(0,5,0,5);TextView row=label(name,17,true);TextView meta=label(item.optString("created_at",channel?"Публикация канала":"Тема группы"),12,false);card.addView(row);card.addView(meta);list.addView(card,cardParams);
            }
        });
    }
    private void createContent(){
        EditText title=field(channel?"Текст публикации":"Название темы");
        EditText body=field("Описание или текст (необязательно)");
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.addView(title);box.addView(body);
        new AlertDialog.Builder(this).setTitle(channel?"Новая публикация":"Новая тема").setView(box)
            .setNegativeButton("Отмена",null).setPositiveButton("Создать",(d,w)->{
                if(title.getText().toString().trim().isEmpty()){Toast.makeText(this,"Заполни название или текст",Toast.LENGTH_SHORT).show();return;}
                String endpoint=channel?"channels/"+id+"/posts":"groups/"+id+"/topics";
                ApiClient.request("POST",endpoint,ApiClient.json(channel?"body":"title",title.getText().toString().trim(),"description",body.getText().toString().trim()),SessionStore.token(this),(data,error)->{
                    Toast.makeText(this,error==null?"Создано":error,Toast.LENGTH_LONG).show();if(error==null)loadContent();
                });
            }).show();
    }
    private void editSettings(){
        EditText name=field("Новое название");name.setText(titleText==null?"":titleText);
        EditText description=field("Описание");LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.addView(name);box.addView(description);
        new AlertDialog.Builder(this).setTitle("Настройки").setView(box).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",(d,w)->{
            String endpoint=(channel?"channels/":"groups/")+id;
            ApiClient.request("PATCH",endpoint,ApiClient.json("title",name.getText().toString().trim(),"description",description.getText().toString().trim()),SessionStore.token(this),(data,error)->Toast.makeText(this,error==null?"Сохранено":error,Toast.LENGTH_LONG).show());
        }).show();
    }
    private void loadSublist(){
        String endpoint=(channel?"channels/":"groups/")+id+(channel?"/subscribers":"/members");
        ApiClient.request("GET",endpoint,null,SessionStore.token(this),(data,error)->{
            if(error!=null){Toast.makeText(this,error,Toast.LENGTH_LONG).show();return;}
            JSONArray arr=data.optJSONArray(channel?"subscribers":"members");if(arr==null)arr=data.optJSONArray("data");
            StringBuilder s=new StringBuilder();if(arr!=null)for(int i=0;i<arr.length();i++){JSONObject u=arr.optJSONObject(i);if(u!=null)s.append(u.optString("username",u.optString("name","Участник"))).append("\n");}
            new AlertDialog.Builder(this).setTitle(channel?"Подписчики":"Участники").setMessage(s.length()==0?"Список пуст.":s.toString()).setPositiveButton("OK",null).show();
        });
    }
    private void inviteMember(){
        EditText user=field("ID пользователя");new AlertDialog.Builder(this).setTitle("Добавить участника").setView(user).setNegativeButton("Отмена",null).setPositiveButton("Добавить",(d,w)->{
            ApiClient.request("POST","groups/"+id+"/members",ApiClient.json("user_id",user.getText().toString().trim()),SessionStore.token(this),(data,error)->Toast.makeText(this,error==null?"Участник добавлен":error,Toast.LENGTH_LONG).show());
        }).show();
    }
}
