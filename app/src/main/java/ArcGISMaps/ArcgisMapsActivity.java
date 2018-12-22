package ArcGISMaps;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.MatrixCursor;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.text.Html;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.annakorowajczykapps.mapscomparison.R;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.geometry.Envelope;
import com.esri.arcgisruntime.geometry.Geometry;
import com.esri.arcgisruntime.geometry.Multipoint;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.Viewpoint;
import com.esri.arcgisruntime.mapping.view.Callout;
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.mapping.view.IdentifyGraphicsOverlayResult;
import com.esri.arcgisruntime.mapping.view.LocationDisplay;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.mapping.view.ViewpointChangedEvent;
import com.esri.arcgisruntime.mapping.view.ViewpointChangedListener;
import com.esri.arcgisruntime.mapping.view.WrapAroundMode;
import com.esri.arcgisruntime.symbology.PictureMarkerSymbol;
import com.esri.arcgisruntime.symbology.SimpleLineSymbol;
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol;
import com.esri.arcgisruntime.tasks.geocode.GeocodeParameters;
import com.esri.arcgisruntime.tasks.geocode.GeocodeResult;
import com.esri.arcgisruntime.tasks.geocode.LocatorTask;
import com.esri.arcgisruntime.tasks.geocode.SuggestParameters;
import com.esri.arcgisruntime.tasks.geocode.SuggestResult;
import com.esri.arcgisruntime.util.ListenableList;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;



public class ArcgisMapsActivity extends AppCompatActivity {

    private final String[] reqPermissions =
            new String[] { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION };

    private final String TAG = ArcgisMapsActivity.class.getSimpleName();

    private final String COLUMN_NAME_ADDRESS = "address";

    private final String[] mColumnNames = { BaseColumns._ID, COLUMN_NAME_ADDRESS };

    private SearchView mPoiSearchView;

    private SearchView mProximitySearchView;

    private boolean mProximitySearchViewEmpty;

    private String mPoiAddress;

    private Point mPreferredSearchProximity;

    private MapView mMapView;

    private LocationDisplay mLocationDisplay;

    private LocatorTask mLocatorTask;

    private GraphicsOverlay mGraphicsOverlay;

    private SuggestParameters mPoiSuggestParameters;

    private GeocodeParameters mPoiGeocodeParameters;

    private SuggestParameters mProximitySuggestParameters;

    private GeocodeParameters mProximityGeocodeParameters;

    private PictureMarkerSymbol mPinSourceSymbol;

    private Geometry mCurrentExtentGeometry;

    private Callout mCallout;



    // setup a new map at your area of interest.
    private void setupMap() {
        if (mMapView != null) {
            Basemap.Type basemapType = Basemap.Type.STREETS_VECTOR;
            double latitude = 51.759247;
            double longitude = 19.455982;
            int levelOfDetail = 10;
            final ArcGISMap map = new ArcGISMap(basemapType, latitude, longitude, levelOfDetail);
            mMapView.setMap(map);

            // wait for the map to be fully ready and rendered before allowing a place search
            mMapView.addViewpointChangedListener(new ViewpointChangedListener() {
                @Override
                public void viewpointChanged(ViewpointChangedEvent viewpointChangedEvent) {
                    if (graphicsOverlay == null) {
                        // Create a graphics overlay that will show the places found from a search.
                        graphicsOverlay = new GraphicsOverlay();
                        mMapView.getGraphicsOverlays().add(graphicsOverlay);
                        setupSpinner();
                        setupPlaceTouchListener();
                        setupNavigationChangedListener();
                        mMapView.removeViewpointChangedListener(this);
                    }
                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_arcgis_maps);

        // if permissions are not already granted, request permission from the user
        if (!(ContextCompat.checkSelfPermission(ArcgisMapsActivity.this, reqPermissions[0]) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(ArcgisMapsActivity.this, reqPermissions[1])
                == PackageManager.PERMISSION_GRANTED)) {
            int requestCode = 2;
            ActivityCompat.requestPermissions(ArcgisMapsActivity.this, reqPermissions, requestCode);
        }

        // setup the two SearchViews and show text hint
        mPoiSearchView = (SearchView) findViewById(R.id.poi_searchView);
        mPoiSearchView.setIconified(false);
        mPoiSearchView.setFocusable(false);
        mPoiSearchView.setQueryHint(getResources().getString(R.string.search_hint));
        mProximitySearchView = (SearchView) findViewById(R.id.proximity_searchView);
        mProximitySearchView.setIconified(false);
        mProximitySearchView.setFocusable(false);
        mProximitySearchView.setQueryHint(getResources().getString(R.string.proximity_search_hint));
        // setup redo search button
        Button redoSearchButton = (Button) findViewById(R.id.redo_search_button);
        // on redo button click call redoSearchInThisArea
        redoSearchButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                redoSearchInThisArea();
            }
        });

        // define pin drawable
        BitmapDrawable pinDrawable = (BitmapDrawable) ContextCompat.getDrawable(this, R.drawable.pin);
        try {
            mPinSourceSymbol = PictureMarkerSymbol.createAsync(pinDrawable).get();
        } catch (InterruptedException | ExecutionException e) {
            String error = "Error creating PictureMarkerSymbol: " + e.getMessage();
            Log.e(TAG, error);
            Toast.makeText(ArcgisMapsActivity.this, error, Toast.LENGTH_LONG).show();
        }
        // set pin to half of native size
        mPinSourceSymbol.setWidth(19f);
        mPinSourceSymbol.setHeight(72f);

        // instantiate flag proximity search view flag
        mProximitySearchViewEmpty = true;

        // create a LocatorTask from an online service
        mLocatorTask = new LocatorTask(getString(R.string.world_geocode_service));

        // inflate MapView from layout
        mMapView = (MapView) findViewById(R.id.mapView);
        // disable map wraparound
        mMapView.setWrapAroundMode(WrapAroundMode.DISABLED);
        // create a map with the BasemapType topographic
        final ArcGISMap map = new ArcGISMap(Basemap.createTopographic());
        // set the map to be displayed in this view
        mMapView.setMap(map);
        // add listener to update extent when viewpoint has changed
        mMapView.addViewpointChangedListener(new ViewpointChangedListener() {
            @Override public void viewpointChanged(ViewpointChangedEvent viewpointChangedEvent) {
                // get the current map extent
                mCurrentExtentGeometry = mMapView.getCurrentViewpoint(Viewpoint.Type.BOUNDING_GEOMETRY).getTargetGeometry();
            }
        });
        // add listener to handle callouts
        mMapView.setOnTouchListener(new DefaultMapViewOnTouchListener(this, mMapView) {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent motionEvent) {
                showCallout(motionEvent);
                return true;
            }
        });
        // setup and start location display
        mLocationDisplay = mMapView.getLocationDisplay();
        mLocationDisplay.setAutoPanMode(LocationDisplay.AutoPanMode.RECENTER);
        mLocationDisplay.startAsync();
        // initially use device location to focus POI search
        final Point[] currentLocation = new Point[1];
        mLocationDisplay.addLocationChangedListener(new LocationDisplay.LocationChangedListener() {
            @Override public void onLocationChanged(LocationDisplay.LocationChangedEvent locationChangedEvent) {
                currentLocation[0] = mLocationDisplay.getMapLocation();
                // only update preferredSearchLocation if device has moved
                if (!currentLocation[0].equals(mLocationDisplay.getMapLocation(), 100) || mPreferredSearchProximity == null) {
                    mPreferredSearchProximity = mLocationDisplay.getMapLocation();
                }
            }
        });
        // define the graphics overlay
        mGraphicsOverlay = new GraphicsOverlay();

        setupPoi();
        setupProximity();
        setupMap();

    }

    private GraphicsOverlay graphicsOverlay;
    private LocatorTask locator = new LocatorTask("http://geocode.arcgis.com/arcgis/rest/services/World/GeocodeServer");
    private Spinner spinner;



    // The Spinner shows the possible categories to search for. When the user selects a
    // category call findPlaces with the category.
    private void setupSpinner() {
        spinner = findViewById(R.id.spinner);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                findPlaces(adapterView.getItemAtPosition(i).toString());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
        findPlaces(spinner.getSelectedItem().toString());
    }

    // Provide additional information about a specific place if the user taps that graphic.
    private void setupPlaceTouchListener() {
        mMapView.setOnTouchListener(new DefaultMapViewOnTouchListener(this, mMapView) {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent motionEvent) {

                // Dismiss a prior callout.
                mMapView.getCallout().dismiss();

                // get the screen point where user tapped
                final android.graphics.Point screenPoint = new android.graphics.Point(Math.round(motionEvent.getX()), Math.round(motionEvent.getY()));

                // identify graphics on the graphics overlay
                final ListenableFuture<IdentifyGraphicsOverlayResult> identifyGraphic = mMapView.identifyGraphicsOverlayAsync(graphicsOverlay, screenPoint, 10.0, false, 2);

                identifyGraphic.addDoneListener(() -> {
                    try {
                        IdentifyGraphicsOverlayResult graphicsResult = identifyGraphic.get();
                        // get the list of graphics returned by identify graphic overlay
                        List<Graphic> graphicList = graphicsResult.getGraphics();

                        // get the first graphic selected and show its attributes with a callout
                        if (!graphicList.isEmpty()){
                            showCalloutAtLocation(graphicList.get(0), mMapView.screenToLocation(screenPoint));
                        }
                    } catch (InterruptedException | ExecutionException exception) {
                        exception.printStackTrace();
                    }
                });
                return super.onSingleTapConfirmed(motionEvent);
            }
        });
    }

    private void setupNavigationChangedListener() {
        mMapView.addNavigationChangedListener(navigationChangedEvent -> {
            if (!navigationChangedEvent.isNavigating()) {
                // Dismiss a prior callout before doing a new search.
                mMapView.getCallout().dismiss();
                findPlaces(spinner.getSelectedItem().toString());
            }
        });
    }

    // Show the selected graphic with its attributes in a callout
    private void showCalloutAtLocation(Graphic graphic, Point mapPoint) {
        Callout callout = mMapView.getCallout();
        TextView calloutContent = new TextView(getApplicationContext());

        callout.setLocation(graphic.computeCalloutLocation(mapPoint, mMapView));
        calloutContent.setTextColor(Color.BLACK);
        calloutContent.setText(Html.fromHtml("<b>" + graphic.getAttributes().get("PlaceName").toString() + "</b><br>" + graphic.getAttributes().get("Place_addr").toString()));
        callout.setContent(calloutContent);
        callout.show();
    }

    // Find places of the requested category within 50 kilometers of the center of the current map view.
    private void findPlaces(String placeCategory) {
        GeocodeParameters parameters = new GeocodeParameters();
        Point searchPoint;

        // Set the current map extent as the current location. The search is limited to a
        // radius of 50 kilometers around this point.
        if (mMapView.getVisibleArea() != null) {
            searchPoint = mMapView.getVisibleArea().getExtent().getCenter();
            if (searchPoint == null) {
                return;
            }
        } else {
            return;
        }
        parameters.setPreferredSearchLocation(searchPoint);

        // We're interested in the top 25 nearest places.
      //  parameters.setMaxResults(25);

        // Return the place name and address fields in the results.
        List<String> outputAttributes = parameters.getResultAttributeNames();
        outputAttributes.add("Place_addr");
        outputAttributes.add("PlaceName");

        // Execute the search and add the places to the graphics overlay.
        final ListenableFuture<List<GeocodeResult>> results = locator.geocodeAsync(placeCategory, parameters);
        results.addDoneListener(() -> {
            try {
                ListenableList<Graphic> graphics = graphicsOverlay.getGraphics();
                graphics.clear();
                List<GeocodeResult> places = results.get();
                for (GeocodeResult result : places) {

                    // Add a graphic representing each location with a simple marker symbol.
                    SimpleMarkerSymbol placeSymbol = new SimpleMarkerSymbol(SimpleMarkerSymbol.Style.CIRCLE, Color.GREEN, 10);
                    placeSymbol.setOutline(new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.WHITE, 2));
                    Graphic graphic = new Graphic(result.getDisplayLocation(), placeSymbol);
                    java.util.Map<String, Object> attributes = result.getAttributes();

                    // Store the location attributes with the graphic for later recall when this location is identified.
                    for (String key : attributes.keySet()) {
                        String value = attributes.get(key).toString();
                        graphic.getAttributes().put(key, value);
                    }
                    graphics.add(graphic);
                }
            } catch (InterruptedException | ExecutionException exception) {
                exception.printStackTrace();
            }
        });
    }


    /**
     * Sets up the POI SearchView. Uses MatrixCursor to show suggestions to the user as the user inputs text.
     */
    private void setupPoi() {

        mPoiSuggestParameters = new SuggestParameters();
        // filter categories for POI
        mPoiSuggestParameters.getCategories().add("POI");
        mPoiGeocodeParameters = new GeocodeParameters();
        // get all attributes
        mPoiGeocodeParameters.getResultAttributeNames().add("*");
        mPoiSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {

            @Override
            public boolean onQueryTextSubmit(String address) {
                // if proximity SearchView text box is empty, use the device location
                if (mProximitySearchViewEmpty) {
                    mPreferredSearchProximity = mLocationDisplay.getMapLocation();
                    mProximitySearchView.setQuery("Using current location...", false);
                }
                // keep track of typed address
                mPoiAddress = address;
                // geocode typed address
                geoCodeTypedAddress(address);
                // clear focus from search views
                mPoiSearchView.clearFocus();
                mProximitySearchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(final String newText) {
                // as long as newText isn't empty, get suggestions from the locatorTask
                if (!newText.equals("")) {
                    mPoiSuggestParameters.setSearchArea(mCurrentExtentGeometry);
                    final ListenableFuture<List<SuggestResult>> suggestionsFuture = mLocatorTask
                            .suggestAsync(newText, mPoiSuggestParameters);
                    suggestionsFuture.addDoneListener(new Runnable() {

                        @Override public void run() {
                            try {
                                // get the results of the async operation
                                List<SuggestResult> suggestResults = suggestionsFuture.get();

                                if (!suggestResults.isEmpty()) {
                                    MatrixCursor suggestionsCursor = new MatrixCursor(mColumnNames);
                                    int key = 0;
                                    // add each poi_suggestion result to a new row
                                    for (SuggestResult result : suggestResults) {
                                        suggestionsCursor.addRow(new Object[] { key++, result.getLabel() });
                                    }
                                    // define SimpleCursorAdapter
                                    String[] cols = new String[] { COLUMN_NAME_ADDRESS };
                                    int[] to = new int[] { R.id.suggestion_address };
                                    final SimpleCursorAdapter suggestionAdapter = new SimpleCursorAdapter(ArcgisMapsActivity.this,
                                            R.layout.suggestion, suggestionsCursor, cols, to, 0);
                                    mPoiSearchView.setSuggestionsAdapter(suggestionAdapter);
                                    // handle a poi_suggestion being chosen
                                    mPoiSearchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
                                        @Override public boolean onSuggestionSelect(int position) {
                                            return false;
                                        }

                                        @Override public boolean onSuggestionClick(int position) {
                                            // get the selected row
                                            MatrixCursor selectedRow = (MatrixCursor) suggestionAdapter.getItem(position);
                                            // get the row's index
                                            int selectedCursorIndex = selectedRow.getColumnIndex(COLUMN_NAME_ADDRESS);
                                            // get the string from the row at index
                                            mPoiAddress = selectedRow.getString(selectedCursorIndex);
                                            mPoiSearchView.setQuery(mPoiAddress, true);
                                            return true;
                                        }
                                    });
                                } else {
                                    mPoiAddress = newText;
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Geocode suggestion error: " + e.getMessage());
                            }
                        }
                    });
                }
                return true;
            }
        });
    }

    /**
     * Sets up the proximity SearchView. Uses MatrixCursor to show suggestions to the user as the user inputs text.
     */
    private void setupProximity() {

        mProximitySuggestParameters = new SuggestParameters();
        mProximitySuggestParameters.getCategories().add("Populated Place");
        mProximityGeocodeParameters = new GeocodeParameters();
        // get all attributes
        mProximityGeocodeParameters.getResultAttributeNames().add("*");
        mProximitySearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String address) {
                geoCodeTypedAddress(address);
                // clear focus from search views
                mPoiSearchView.clearFocus();
                mProximitySearchView.clearFocus();
                return true;
            }

            @Override public boolean onQueryTextChange(String newText) {
                // as long as newText isn't empty, get suggestions from the locatorTask
                if (!newText.equals("")) {
                    mProximitySearchViewEmpty = false;
                    final ListenableFuture<List<SuggestResult>> suggestionsFuture = mLocatorTask
                            .suggestAsync(newText, mProximitySuggestParameters);
                    suggestionsFuture.addDoneListener(new Runnable() {

                        @Override public void run() {
                            try {
                                // get the list of suggestions
                                List<SuggestResult> suggestResults = suggestionsFuture.get();
                                MatrixCursor suggestionsCursor = new MatrixCursor(mColumnNames);
                                int key = 0;
                                // add each SuggestResult to a new row
                                for (SuggestResult result : suggestResults) {
                                    suggestionsCursor.addRow(new Object[] { key++, result.getLabel() });
                                }
                                // define SimpleCursorAdapter
                                String[] cols = new String[] { COLUMN_NAME_ADDRESS };
                                int[] to = new int[] { R.id.suggestion_address };
                                final SimpleCursorAdapter suggestionAdapter = new SimpleCursorAdapter(ArcgisMapsActivity.this,
                                        R.layout.suggestion, suggestionsCursor, cols, to, 0);
                                mProximitySearchView.setSuggestionsAdapter(suggestionAdapter);
                                mProximitySearchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
                                    @Override public boolean onSuggestionSelect(int position) {
                                        return false;
                                    }

                                    @Override public boolean onSuggestionClick(int position) {
                                        // get the selected row
                                        MatrixCursor selectedRow = (MatrixCursor) suggestionAdapter.getItem(position);
                                        // get the row's index
                                        int selectedCursorIndex = selectedRow.getColumnIndex(COLUMN_NAME_ADDRESS);
                                        // get the string from the row at index
                                        final String address = selectedRow.getString(selectedCursorIndex);
                                        mLocatorTask.addDoneLoadingListener(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (mLocatorTask.getLoadStatus() == LoadStatus.LOADED) {
                                                    // geocode the selected address to get location of address
                                                    final ListenableFuture<List<GeocodeResult>> geocodeFuture = mLocatorTask
                                                            .geocodeAsync(address, mProximityGeocodeParameters);
                                                    geocodeFuture.addDoneListener(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            try {
                                                                // Get the results of the async operation
                                                                List<GeocodeResult> geocodeResults = geocodeFuture.get();
                                                                if (geocodeResults.size() > 0) {
                                                                    // use geocodeResult to focus search area
                                                                    GeocodeResult geocodeResult = geocodeResults.get(0);
                                                                    // update preferred search area to the geocode result
                                                                    mPreferredSearchProximity = geocodeResult.getDisplayLocation();
                                                                    mPoiGeocodeParameters.setSearchArea(mPreferredSearchProximity);
                                                                    // set the address string to the SearchView, but don't submit as a query
                                                                    mProximitySearchView.setQuery(address, false);
                                                                    // call POI search query
                                                                    mPoiSearchView.setQuery(mPoiAddress, true);
                                                                    // clear focus from search views
                                                                    mProximitySearchView.clearFocus();
                                                                    mPoiSearchView.clearFocus();
                                                                } else {
                                                                    Toast.makeText(getApplicationContext(),
                                                                            getString(R.string.location_not_found) + address, Toast.LENGTH_LONG).show();
                                                                }
                                                            } catch (InterruptedException | ExecutionException e) {
                                                                Log.e(TAG, "Geocode error: " + e.getMessage());
                                                                Toast.makeText(getApplicationContext(), getString(R.string.geo_locate_error),
                                                                        Toast.LENGTH_LONG).show();
                                                            }
                                                        }
                                                    });
                                                }
                                            }
                                        });
                                        return true;
                                    }
                                });
                            } catch (Exception e) {
                                Log.e(TAG, "Geocode suggestion error: " + e.getMessage());
                            }
                        }
                    });
                    // if search view is empty, set flag
                } else {
                    mProximitySearchViewEmpty = true;
                }
                return true;
            }
        });
    }

    /**
     * Performs a search for the POI listed in the SearchView, using the MapView's current extent to inform the search.
     */
    private void redoSearchInThisArea() {
        // set center of current extent to preferred search proximity
        mPreferredSearchProximity = mCurrentExtentGeometry.getExtent().getCenter();
        mPoiGeocodeParameters.setSearchArea(mCurrentExtentGeometry);
        mProximitySearchView.setQuery(getString(R.string.searching_by_area), false);
        // use most recent POI address
        geoCodeTypedAddress(mPoiAddress);
    }

    /**
     * Identifies the Graphic at the tapped point. Gets attribute of that Graphic and assigns it to a Callout, which is
     * then displayed.
     *
     * @param motionEvent from onSingleTapConfirmed
     */
    private void showCallout(MotionEvent motionEvent) {
        // get the screen point
        android.graphics.Point screenPoint = new android.graphics.Point(Math.round(motionEvent.getX()),
                Math.round(motionEvent.getY()));
        // convert to map point
        final Point mapPoint = mMapView.screenToLocation(screenPoint);
        // from the graphics overlay, get graphics near the tapped location
        final ListenableFuture<IdentifyGraphicsOverlayResult> identifyResultsFuture = mMapView
                .identifyGraphicsOverlayAsync(mGraphicsOverlay, screenPoint, 10, false);
        identifyResultsFuture.addDoneListener(new Runnable() {
            @Override public void run() {
                try {
                    IdentifyGraphicsOverlayResult identifyGraphicsOverlayResult = identifyResultsFuture.get();
                    List<Graphic> graphics = identifyGraphicsOverlayResult.getGraphics();
                    // if a graphic has been identified
                    if (graphics.size() > 0) {
                        //get the first graphic identified
                        Graphic identifiedGraphic = graphics.get(0);
                        // create a TextView for the Callout
                        TextView calloutContent = new TextView(getApplicationContext());
                        calloutContent.setTextColor(Color.BLACK);
                        // set the text of the Callout to graphic's attributes
                        calloutContent.setText(identifiedGraphic.getAttributes().get("PlaceName").toString() + "\n"
                                + identifiedGraphic.getAttributes().get("StAddr").toString());
                        // get Callout and set its options: animateCallout: true, recenterMap: false, animateRecenter: false
                        mCallout = mMapView.getCallout();
                        mCallout.setShowOptions(new Callout.ShowOptions(true, false, false));
                        // set the leader position and show the callout
                        mCallout.setLocation(identifiedGraphic.computeCalloutLocation(mapPoint, mMapView));
                        mCallout.setContent(calloutContent);
                        mCallout.show();
                    } else {
                        mCallout.dismiss();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Identify error: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Geocode an address passed in by the user.
     *
     * @param address read in from searchViews
     */
    private void geoCodeTypedAddress(final String address) {
        // check that address isn't null
        if (address != null) {
            // POI geocode parameters set from proximity SearchView or, if empty, device location
            mPoiGeocodeParameters.setPreferredSearchLocation(mPreferredSearchProximity);
            mPoiGeocodeParameters.setSearchArea(mPreferredSearchProximity);
            // Execute async task to find the address
            mLocatorTask.addDoneLoadingListener(new Runnable() {
                @Override
                public void run() {
                    if (mLocatorTask.getLoadStatus() == LoadStatus.LOADED) {
                        // Call geocodeAsync passing in an address
                        final ListenableFuture<List<GeocodeResult>> geocodeResultListenableFuture = mLocatorTask
                                .geocodeAsync(address, mPoiGeocodeParameters);
                        geocodeResultListenableFuture.addDoneListener(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    // Get the results of the async operation
                                    List<GeocodeResult> geocodeResults = geocodeResultListenableFuture.get();
                                    if (geocodeResults.size() > 0) {
                                        displaySearchResult(geocodeResults);
                                    } else {
                                        Toast.makeText(getApplicationContext(), getString(R.string.location_not_found) + address,
                                                Toast.LENGTH_LONG).show();
                                    }
                                } catch (InterruptedException | ExecutionException e) {
                                    Log.e(TAG, "Geocode error: " + e.getMessage());
                                    Toast.makeText(getApplicationContext(), getString(R.string.geo_locate_error), Toast.LENGTH_LONG)
                                            .show();
                                }
                            }
                        });
                    } else {
                        Log.i(TAG, "Trying to reload locator task");
                        mLocatorTask.retryLoadAsync();
                    }
                }
            });
            mLocatorTask.loadAsync();
        }
    }

    /**
     * Turns a list of GeocodeResults into Points and adds them to a GraphicOverlay which is then drawn on the map. The
     * points are added to a multipoint used to calculate a viewpoint.
     *
     * @param geocodeResults as a list
     */
    private void displaySearchResult(List<GeocodeResult> geocodeResults) {
        // dismiss any callout
        if (mMapView.getCallout() != null && mMapView.getCallout().isShowing()) {
            mMapView.getCallout().dismiss();
        }
        // clear map of existing graphics
        mMapView.getGraphicsOverlays().clear();
        mGraphicsOverlay.getGraphics().clear();
        // create a list of points from the geocode results
        List<Point> resultPoints = new ArrayList<>();
        for (GeocodeResult result : geocodeResults) {
            // create graphic object for resulting location
            Point resultPoint = result.getDisplayLocation();
            Graphic resultLocGraphic = new Graphic(resultPoint, result.getAttributes(), mPinSourceSymbol);
            // add graphic to location layer
            mGraphicsOverlay.getGraphics().add(resultLocGraphic);
            resultPoints.add(resultPoint);
        }
        // add result points to a Multipoint and get an envelope surrounding it
        Multipoint resultsMultipoint = new Multipoint(resultPoints);
        Envelope resultsEnvelope = resultsMultipoint.getExtent();
        // add a 25% buffer to the extent Envelope of result points
        Envelope resultsEnvelopeWithBuffer = new Envelope(resultsEnvelope.getCenter(), resultsEnvelope.getWidth() * 1.25,
                resultsEnvelope.getHeight() * 1.25);
        // zoom map to result over 3 seconds
        mMapView.setViewpointAsync(new Viewpoint(resultsEnvelopeWithBuffer), 3);
        // set the graphics overlay to the map
        mMapView.getGraphicsOverlays().add(mGraphicsOverlay);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // if request is cancelled, the result arrays are empty
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            mLocationDisplay.startAsync();
        } else {
            // if permission was denied, show toast to inform user what was chosen
            Toast.makeText(ArcgisMapsActivity.this, getResources().getString(R.string.location_permission_denied),
                    Toast.LENGTH_SHORT).show();
        }
    }



    @Override
    protected void onPause() {
        if (mMapView != null) {
            mMapView.pause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mMapView != null) {
            mMapView.resume();
        }
    }

    @Override
    protected void onDestroy() {
        if (mMapView != null) {
            mMapView.dispose();
        }
        super.onDestroy();
    }

  /*  @Override
    protected void onPause() {
        super.onPause();
        mMapView.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mMapView.resume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMapView.dispose();
    } */

}
