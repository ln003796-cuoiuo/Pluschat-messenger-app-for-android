package ru.pluschat.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class CommunityActivity extends Activity {
    private LinearLayout list;
    private boolean channel;
    private final int bg=0xff0b0d12, panel=0xff131720, white=0xfff5f7fb, muted=0xff98a2b5, accent=0xff6f5bea;
    @Override public void onCreate(Bundle state){super.onCreate(state);channel=getIntent().getBooleanExtra("channel",false);render();}
    private GradientDrawable shape(int c){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(18);return d;}
    private TextView label(String s,int size,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextColor(bold?white:muted);t.setTextSize(size);if(bold)t.setTypeface(null,android.graphics.Typeface.BOLD);return t;}
    private Button button(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(16);b.setTypeface(null,android.graphics.Typeface.BOLD);b.setTextColor(Color.WHITE);b.setPadding(18,12,18,12);b.setMinHeight(60);b.setMinimumHeight(60);b.setBackground(shape(accent));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,6,0,6);b.setLayoutParams(p);b.setOnClickListener(v->r.run());return b;}
    private EditText field(String hint){EditText e=new EditText(this);e.setHint(hint);e.setTextColor(white);e.setHintTextColor(muted);e.setTextSize(16);e.setPadding(16,14,16,14);e.setMinHeight(60);e.setBackground(shape(0xff1a2030));return e;}
    private void render(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(20,24,20,24);root.setBackgroundColor(bg);getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);if(android.os.Build.VERSION.SDK_INT>=23)getWindow().getDecorView().setSystemUiVisibility(0);
        TextView heading=label("Сообщества",25,true);root.addView(heading);
        root.addView(label("Места, где собираются твои люди",13,false));
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);
        EditText name=field(channel?"Название канала":"Название группы");
        EditText description=field("Описание (необязательно)");
        EditText visibility=field("Тип: private или public");visibility.setText("private");
        form.addView(name);form.addView(description);form.addView(visibility);
        form.setPadding(0,14,0,8);root.addView(form);
        root.addView(button(channel?"＋  Создать канал":"＋  Создать группу",()->{
            if(name.getText().toString().trim().isEmpty()){Toast.makeText(this,"Укажи название",Toast.LENGTH_SHORT).show();return;}
            JSONObject payload=ApiClient.json("title",name.getText().toString().trim(),"description",description.getText().toString().trim(),"visibility",visibility.getText().toString().trim());
            ApiClient.request("POST",channel?"channels":"groups",payload,SessionStore.token(this),(data,error)->{
                if(error!=null){Toast.makeText(this,error,Toast.LENGTH_LONG).show();return;}
                name.setText("");description.setText("");Toast.makeText(this,"Создано",Toast.LENGTH_SHORT).show();load();
            });
        }));
        root.addView(button("↻  Обновить список",this::load));
        ScrollView scroll=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(button("Назад",this::finish));setContentView(root);load();
    }
    private void load(){
        if(list==null)return;list.removeAllViews();
        ApiClient.request("GET",channel?"channels":"groups",null,SessionStore.token(this),(data,error)->{
            if(error!=null){list.addView(label(error,14,false));return;}
            JSONArray arr=data.optJSONArray(channel?"channels":"groups");if(arr==null)arr=data.optJSONArray("data");
            if(arr==null||arr.length()==0){list.addView(label("Пока пусто. После подключения API здесь появятся твои "+(channel?"каналы.":"группы."),14,false));return;}
            for(int i=0;i<arr.length();i++){JSONObject item=arr.optJSONObject(i);if(item==null)continue;
                String title=item.optString("title",item.optString("name",channel?"Канал":"Группа"));
                LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(16,14,16,14);card.setBackground(shape(panel));LinearLayout.LayoutParams cardParams=new LinearLayout.LayoutParams(-1,-2);cardParams.setMargins(0,5,0,5);TextView row=label("◉  "+title,17,true);TextView desc=label(item.optString("description",channel?"Публикации канала":"Участники и темы"),13,false);card.addView(row);card.addView(desc);card.setOnClickListener(v->{android.content.Intent intent=new android.content.Intent(this,CommunityDetailActivity.class);intent.putExtra("community_id",item.optString("id",item.optString("group_id",item.optString("channel_id",""))));intent.putExtra("community_title",title);intent.putExtra("channel",channel);startActivity(intent);});list.addView(card,cardParams);
            }
        });
    }
}
