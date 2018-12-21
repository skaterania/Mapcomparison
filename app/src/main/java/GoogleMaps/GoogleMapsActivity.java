package GoogleMaps;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.annakorowajczykapps.mapscomparison.R;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.location.LocationListener;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMapOptions;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.util.List;

public class GoogleMapsActivity extends FragmentActivity implements
        OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    private GoogleMap mMap;
    private GoogleApiClient googleApiClient;
    private LocationRequest locationRequest;
    private Location lastLocation;
    private Marker currentUserLocationMarker;
    private static final int Request_User_Location_Code = 99;
    private double latitude, longtitude;
    private int ProximityRadius = 10000;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_google_maps);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){

            checkUserLocationPermission();

        }

        //dodane zamiast tego co zakomentowane ponizej

       GoogleMapOptions options = new GoogleMapOptions();
        //options.mapType(GoogleMap.MAP_TYPE_TERRAIN);
        options.zoomControlsEnabled(true);
        options.compassEnabled(true);
        SupportMapFragment mapFragment = SupportMapFragment.newInstance(options);
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction()
                .replace(R.id.map, mapFragment);

        ft.commit();
        mapFragment.getMapAsync(this);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
      /*  SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this); */
    }

    public void onClick(View v){

        String hospital = "hospital", school = "school", restaurant = "restaurant";
        Object transferData[] = new Object[2];
        GetNearbyPlaces getNearbyPlaces = new GetNearbyPlaces();


        switch (v.getId()){

            case R.id.search_adress:
                EditText addressField = (EditText) findViewById(R.id.location_search);
                String address = addressField.getText().toString();

                List<Address> addressList = null;
                MarkerOptions userMarkerOptions = new MarkerOptions();

                if(!TextUtils.isEmpty(address)){

                    Geocoder geocoder = new Geocoder(this);

                    try {

                        addressList = geocoder.getFromLocationName(address, 6);

                        if(addressList != null){

                            for(int i=0; i<addressList.size(); i++){

                                Address userAddress = addressList.get(i);
                                LatLng latLng = new LatLng(userAddress.getLatitude(), userAddress.getLongitude());

                                userMarkerOptions.position(latLng);
                                userMarkerOptions.title(address);
                                userMarkerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE));

                                mMap.addMarker(userMarkerOptions);

                                mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
                                mMap.animateCamera(CameraUpdateFactory.zoomTo(10));

                            }

                        } // if there is no place which user want to search
                        else{

                            Toast.makeText(this, "Location not found", Toast.LENGTH_SHORT).show();

                        }

                    } catch (IOException e) {

                        e.printStackTrace();
                    }

                }
                else{

                    Toast.makeText(this, "Please write any location...", Toast.LENGTH_SHORT).show();

                }
                break;

         /*   case  R.id.hospitals_nearby:
                mMap.clear();
                String url = getUrl(latitude, longtitude, hospital);
                transferData[0] = mMap;
                transferData[1] = url;

                getNearbyPlaces.execute(transferData);
                Toast.makeText(this, "Showing nearby hospitals...", Toast.LENGTH_SHORT).show();

                break;


            case  R.id.schools_nearby:
                mMap.clear();
                url = getUrl(latitude, longtitude, school);
                transferData[0] = mMap;
                transferData[1] = url;

                getNearbyPlaces.execute(transferData);
                Toast.makeText(this, "Showing nearby schools...", Toast.LENGTH_SHORT).show();


                break; */

            case  R.id.restaurants_nearby:
                mMap.clear();
               String url = getUrl(latitude, longtitude, restaurant);
                transferData[0] = mMap;
                transferData[1] = url;

                getNearbyPlaces.execute(transferData);
                Toast.makeText(this, "Showing nearby restaurants...", Toast.LENGTH_SHORT).show();


                break;
        }


    }

    private String getUrl(double latitude, double longtitude, String nearbyPlace){

    StringBuilder googleURL = new StringBuilder("https://maps.googleapis.com/maps/api/place/nearbysearch/json?");
    googleURL.append("location=" + latitude + "," + longtitude);
    googleURL.append("&radius=" + ProximityRadius);
    googleURL.append("&type=" + nearbyPlace);
    googleURL.append("&sensor=true");
    googleURL.append("&key=" + "AIzaSyDV3AGKB8lFUT6v5NmgCs1SqadtfxyvvYM");

    Log.d("GoogleMapsActivity",googleURL.toString());

    return googleURL.toString();




    }

    @Override
    public void onMapReady(GoogleMap googleMap) {

        mMap = googleMap;

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)== PackageManager.PERMISSION_GRANTED) {

            buildGoogleApiClient();

            mMap.setMyLocationEnabled(true);


        }

        //dodany marker przykladowy

        LatLng lodz = new LatLng(51.759445, 19.457216);

        mMap.addMarker(new MarkerOptions().position(lodz)
                .title("Lodz"));

        //randomowe dodawanie markerow przez uzytkownika

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(lodz, 17));
        mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng latLng) {

                mMap.addMarker(new MarkerOptions().position(latLng)
                        .title("Random")
                        .snippet("Random"));




            }
        });



    }

    public boolean checkUserLocationPermission(){


        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){ //if we ask user for the permission

            if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)){ //if true

                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, Request_User_Location_Code); // get permission
            }
            else{

                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, Request_User_Location_Code); // get permission


            }
            return false; //if user click dont ask permission then return false

        }
        else{

            return true;
        }

    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        switch(requestCode){

            case Request_User_Location_Code:
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){

                    if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==PackageManager.PERMISSION_GRANTED){

                        if(googleApiClient == null){

                            buildGoogleApiClient();
                        }
                        mMap.setMyLocationEnabled(true);

                    }

                }
                else{ //if permission denied

                    Toast.makeText(this, "Permission denied...", Toast.LENGTH_SHORT).show();
                }
                return;

        }
    }

    protected synchronized void buildGoogleApiClient(){ //metoda tworzy nowego klienta, wywolywana wyzej jesli googleApiClient jest nullem

        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();


        googleApiClient.connect();

    }

    @Override
    public void onLocationChanged(Location location) {

        latitude = location.getLatitude();
        longtitude = location.getLongitude();

        lastLocation = location;

        if(currentUserLocationMarker != null) { //if it is set to other location


            currentUserLocationMarker.remove(); // to musimy usunac marker
        }

        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude()); //ustawiamy dla nowej lokalizacji

        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(latLng);
        markerOptions.title("user Current Location");
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN));

        currentUserLocationMarker = mMap.addMarker(markerOptions);

        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        mMap.animateCamera(CameraUpdateFactory.zoomBy(70));

        if(googleApiClient != null){

            LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, this);

        }



    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {

        locationRequest = new LocationRequest();
        locationRequest.setInterval(1100);
        locationRequest.setFastestInterval(1100);
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)== PackageManager.PERMISSION_GRANTED)
        {
            LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);

        }



    }


    @Override
    public void onConnectionSuspended(int i) {

       // super.onStart();
      //  googleApiClient.connect();

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

        //super.onStop();
        //googleApiClient.disconnect();
    }
}
