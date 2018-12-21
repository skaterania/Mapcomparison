package GoogleMaps;

import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DataParser {

    private HashMap<String, String> getSingleNearbyPlace(JSONObject googlePLaceJSON){

        HashMap<String, String> googlePlaceMap = new HashMap<>(); //use this to put the data
        String NameOfPlace = "-NA-";
        String vicinity = "-NA-";
        String latitude = "";
        String longtitue = "";
        String reference = "";

        try {

            if(!googlePLaceJSON.isNull("name")){

                NameOfPlace = googlePLaceJSON.getString("name");

            }
            if(!googlePLaceJSON.isNull("vicinity")){

                NameOfPlace = googlePLaceJSON.getString("vicinity");

            }
            latitude = googlePLaceJSON.getJSONObject("geometry").getJSONObject("location").getString("lat");
            longtitue = googlePLaceJSON.getJSONObject("geometry").getJSONObject("location").getString("lng");
            reference = googlePLaceJSON.getString("reference");

            googlePlaceMap.put("place_name", NameOfPlace);
            googlePlaceMap.put("vicinity", vicinity);
            googlePlaceMap.put("lat", latitude);
            googlePlaceMap.put("lng", longtitue);
            googlePlaceMap.put("reference", reference);



        } catch (JSONException e) {
            e.printStackTrace();
        }

        return googlePlaceMap;


    }

    private List<HashMap<String, String>> getAllNearbyPlaces(JSONArray jsonArray){ //the method will store JSONArray data and add into list of hashmaps and fetch places one by one using for loop
                                                                                    // then call getSingleNearbyPlace for each place.
        int counter = jsonArray.length();

        List<HashMap<String, String>>NearbyPlacesList = new ArrayList<>();

        HashMap<String,String> NearbyPlaceMap = null;

        for(int i=0; i<counter; i++){

            try {

                NearbyPlaceMap = getSingleNearbyPlace( (JSONObject)jsonArray.get(i) );
                NearbyPlacesList.add(NearbyPlaceMap);

            } catch (JSONException e) {

                e.printStackTrace();
            }
        }

        return  NearbyPlacesList;



    }

    public  List<HashMap<String, String>> parse(String jSONdata){

        JSONArray jsonArray = null;
        JSONObject jsonObject;

        try {

            jsonObject = new JSONObject(jSONdata);
            jsonArray = jsonObject.getJSONArray("results");

        } catch (JSONException e) {

            e.printStackTrace();
        }

        return getAllNearbyPlaces(jsonArray);


    }



}
