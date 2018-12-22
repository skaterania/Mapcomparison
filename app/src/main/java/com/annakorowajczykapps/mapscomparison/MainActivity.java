package com.annakorowajczykapps.mapscomparison;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.view.View;
import android.widget.Button;

import ArcGISMaps.ArcgisMapsActivity;
import GoogleMaps.GoogleMapsActivity;

public class MainActivity extends AppCompatActivity {

    private Button b;
    private Button a;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        b = (Button)findViewById(R.id.button_google);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Intent intent = new Intent(MainActivity.this, GoogleMapsActivity.class);
                startActivity(intent);


            }
        });

        a = (Button)findViewById(R.id.button_arcgis);
        a.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent intent = new Intent(MainActivity.this, ArcgisMapsActivity.class);
                startActivity(intent);

            }
        });


    }
}
