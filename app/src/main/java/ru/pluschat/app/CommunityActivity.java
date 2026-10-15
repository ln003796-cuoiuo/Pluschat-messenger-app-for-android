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
    private final int bg=Color.rgb(10,12,20), panel=Color.rgb(25,29,43), white=Color.rgb(246,247,255), muted=Color.rgb(157,166,190), accent=Color.rgb(170,153,255);
    @Override public void onCreate(Bundle state){super.onCreate(state);channel=getIntent().getBooleanExtra("channel",false);render();}
    private GradientDrawable shape(int c){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(18);return d;}
    private TextView label(String s,int size,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextColor(bold?white:muted);t.setTextSize(size);if(bold)t.setTypeface(null,android.graphics.Typeface.BOLD);return t;}
    private Button button(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(bg);b.setBackground(shape(accent));b.setOnClickListener(v->r.run());return b;}
    private EditText field(String hint){EditText e=new EditText(this);e.setHint(hint);e.setTextColor(white);e.setHintTextColor(muted);e.setTextSize(15);e.setPadding(14,10,14,10);e.setBackground(shape(panel));return e;}
    private void render(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(20,24,20,20);root.setBackgroundColor(bg);
        TextView heading=label(channel?"Каналы":"Группы",28,true);root.addView(heading);
        root.addView(label(channel?"Публикации для подписчиков":"Общие переписки и темы",14,false));
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);
        EditText name=field(channel?"Название канала":"Название группы");
        EditText description=field("Описание (необязательно)");
        EditText visibility=field("Тип: private или public");visibility.setText("private");
        form.addView(name);form.addView(description);form.addView(visibility);
        root.addView(button(channel?"Создать канал":"Создать группу",()->{
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
