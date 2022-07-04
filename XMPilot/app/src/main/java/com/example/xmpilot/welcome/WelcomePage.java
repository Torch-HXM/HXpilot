package com.example.xmpilot.welcome;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityOptionsCompat;

import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;

import com.example.xmpilot.home.HomePage;
import com.example.xmpilot.R;

public class WelcomePage extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.welcomepage_whole);

        ImageView image_logo = findViewById(R.id.welcomepage_logo);
        Animation logo_come_out = new AlphaAnimation(0.0f,1.0f);
        logo_come_out.setDuration(1500);
        image_logo.setAnimation(logo_come_out);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(WelcomePage.this, HomePage.class));
                finish();
                overridePendingTransition(R.anim.fade_out,R.anim.fade_in);
            }
        },1700);
    }
}