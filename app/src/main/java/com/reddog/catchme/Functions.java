package com.reddog.catchme;

/*
 * Smart WebView is an Open Source project that integrates native features into webview to help create advanced hybrid applications. Available on GitHub (https://github.com/mgks/Android-Constants).
 * Initially developed by Ghazi Khan (https://github.com/mgks) under MIT Open Source License.
 * This program is free to use for private and commercial purposes under MIT License (https://opensource.org/licenses/MIT).
 * Please mention project source or developer credits in your Application's License(s) Wiki.
 * Contribute to the project (https://github.com/mgks/Android-Constants/discussions)
 * Sponsor the project (https://github.com/sponsors/mgks)
 * Giving right credits to developers encourages them to keep improving their projects :)
 */

import static android.content.Context.ACCOUNT_SERVICE;
import static java.lang.Math.round;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintJob;
import android.print.PrintManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.firebase.geofire.GeoFireUtils;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQueryBounds;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.BuildConfig;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.reddog.catchme.utility.Constants;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class Functions implements NavigationView.OnNavigationItemSelectedListener {
    private final SecureRandom random = new SecureRandom();

    /* --- internal functions --- */

    //Getting host name
    public static String aswm_host(String url) {
        if (url == null || url.length() == 0) {
            return "";
        }
        int dslash = url.indexOf("//");
        if (dslash == -1) {
            dslash = 0;
        } else {
            dslash += 2;
        }
        int end = url.indexOf('/', dslash);
        end = end >= 0 ? end : url.length();
        int port = url.indexOf(':', dslash);
        end = (port > 0 && port < end) ? port : end;
        Log.i("SLOG_URL_HOST", url.substring(dslash, end));
        return url.substring(dslash, end);
    }

    public static Pattern url_pattern() {
        return Pattern.compile("(?:^|\\W)((ht|f)tp(s?)://|www\\.)" + "(([\\w\\-]+\\.)+([\\w\\-.~]+/?)*" + "[\\p{Alnum}.,%_=?&#\\-+()\\[\\]*$~@!:/{};']*)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
    }

    public static int aswm_fcm_id() {
        //Date now = new Date();
        //Integer.parseInt(new SimpleDateFormat("ddHHmmss",  Locale.US).format(now));
        return 1;
    }

    // random ID creation function to help get fresh cache every-time webview reloaded
    public String random_id() {
        return new BigInteger(130, random).toString(32);
    }

    // opening URLs inside webview with request
    void aswm_view(String url, Boolean tab, int error_counter, Context context) {
        if (error_counter > 2) {
            exit_app(context);
        } else {
            if (tab) {
                if (Constants.ASWP_TAB) {
                    CustomTabsIntent.Builder intentBuilder = new CustomTabsIntent.Builder();
                    intentBuilder.setStartAnimations(context.getApplicationContext(), android.R.anim.slide_in_left, android.R.anim.slide_out_right);
                    intentBuilder.setExitAnimations(context.getApplicationContext(), android.R.anim.slide_in_left, android.R.anim.slide_out_right);
                    CustomTabsIntent customTabsIntent = intentBuilder.build();
                    try {
                        customTabsIntent.launchUrl(context.getApplicationContext(), Uri.parse(url));
                    } catch (ActivityNotFoundException e) {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse(url));
                        context.startActivity(intent);
                    }
                } else {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(url));
                    context.startActivity(intent);
                }
            } else {
                // check to see whether the url already has query parameters and handle appropriately
                url = url + (url.contains("?") ? "&" : "?") + "rid=" + random_id();
                Constants.asw_view.loadUrl(url);
            }
        }
    }

    /*--- actions based on URL structure ---*/
    public boolean url_actions(WebView view, String url, Context context) {
        boolean a = true;
        // show toast error if not connected to the network
        if (!Constants.ASWP_OFFLINE && !DetectConnection.isInternetAvailable(context)) {
            Toast.makeText(context, context.getString(R.string.check_connection), Toast.LENGTH_SHORT).show();

            // use this in a hyperlink to redirect back to default URL :: href="refresh:android"
        } else if (url.startsWith("refresh:")) {
            String ref_sch = (Uri.parse(url).toString()).replace("refresh:", "");
            if (ref_sch.matches("URL")) {
                Constants.CURR_URL = Constants.ASWV_URL;
            }
            pull_fresh(context);

            // use this in a hyperlink to launch default phone dialer for specific number :: href="tel:+919876543210"
        } else if (url.startsWith("tel:")) {
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse(url));
            context.startActivity(intent);

        } else if (url.startsWith("print:")) {
            print_page(view, view.getTitle(), true, context);

            // use this to open your apps page on google play store app :: href="rate:android"
        } else if (url.startsWith("rate:")) {
            final String app_package = context.getPackageName(); //requesting app package name from Context or Activity object
            try {
                context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + app_package)));
            } catch (ActivityNotFoundException anfe) {
                context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + app_package)));
            }

            // sharing content from your webview to external apps :: href="share:URL" and remember to place the URL you want to share after share:___
        } else if (url.startsWith("share:")) {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, view.getTitle());
            intent.putExtra(Intent.EXTRA_TEXT, view.getTitle() + "\nVisit: " + (Uri.parse(url).toString()).replace("share:", ""));
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_w_friends)));

            // use this in a hyperlink to exit your app :: href="exit:android"
        } else if (url.startsWith("exit:")) {
            exit_app(context);

            // getting location for offline files
        } else if (url.startsWith("offloc:")) {
            String offloc = Constants.ASWV_URL + "?loc=" + get_location(context);
            aswm_view(offloc, false, Constants.asw_error_counter, context);
            Log.d("SLOG_OFFLINE_LOC_REQ", offloc);

            // creating firebase notification for offline files
        } else if (url.startsWith("fcm:")) {
            String fcm = Constants.ASWV_URL + "?fcm=" + fcm_token();
            aswm_view(fcm, false, Constants.asw_error_counter, context);
            Log.d("SLOG_OFFLINE_FCM_TOKEN", fcm);

            // opening external URLs in android default web browser
        } else if (Constants.ASWP_EXTURL && !aswm_host(url).equals(Constants.ASWV_HOST) && !Constants.ASWV_EXC_LIST.contains(aswm_host(url))) {
            aswm_view(url, true, Constants.asw_error_counter, context);

            // set the device orientation on request
        } else if (url.startsWith("orient:")) {
            set_orientation(5, true, context);

            // else return false for no special action
        } else {
            a = false;
        }
        return a;
    }

    // reloading current page
    public void pull_fresh(Context context) {
        aswm_view((!Constants.CURR_URL.equals("") ? Constants.CURR_URL : Constants.ASWV_URL), false, Constants.asw_error_counter, context);
    }

    // changing port view
    @SuppressLint("SourceLockedOrientationActivity")
    public void set_orientation(int orientation, boolean cookie, Context context) { // setting the view port var
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            if (orientation == 1) {
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            } else if (orientation == 2) {
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            } else if (orientation == 5) { //experimental switch
                Constants.ASWV_ORIENTATION = (Constants.ASWV_ORIENTATION == 1 ? 2 : 1);
            } else {
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            }
            if (cookie) {
                set_cookie("ORIENT=" + orientation);
            }
        }
    }

    // setting cookies
    public void set_cookie(String data) {
        //boolean log = true;
        if (Constants.true_online) {
            // cookie manager initialisation
            Constants.cookie_manager = CookieManager.getInstance();
            Constants.cookie_manager.setAcceptCookie(true);
            Constants.cookie_manager.setCookie(Constants.ASWV_URL, data);
            //Log.d("SLOG_COOKIES", Constants.cookie_manager.getCookie(Constants.ASWV_URL));
        }
    }

    //Getting device basic information
    public void get_info() {
        set_cookie("DEVICE=android");
        DeviceDetails dv = new DeviceDetails();
        set_cookie("DEVICE_INFO=" + dv.pull());
        set_cookie("DEV_API=" + Build.VERSION.SDK_INT);
        set_cookie("APP_ID=" + BuildConfig.LIBRARY_PACKAGE_NAME);
        set_cookie("APP_VER=" + BuildConfig.BUILD_TYPE + "/" + BuildConfig.VERSION_NAME);
    }

    public String getLoggedInUserName(Context context){
        AccountManager manager = (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);
        Account[] list = manager.getAccounts();
        String gmail = null;
        Log.i("LIST_OF_ACCOUNTS", "HERE----"+list.length);
        for(Account account: list)
        {
            Log.i("LIST_OF_ACCOUNTS", "HERE----"+account+list.length);
            if(account.name.contains("@gmail.com"))
            {
                gmail = account.name;
                break;
            }
        }return gmail;
    }

    public String getDeviceInfo(Context context) {
        String android_id = Settings.Secure.getString(context.getApplicationContext().getContentResolver(),
                Settings.Secure.ANDROID_ID);
        Log.i("DEVICE_OWNER_NAME", "HERE----"+android_id);
        return android_id;
    }

    public void upsertLocationDeviceData(String deviceId, String deviceLocation, Context context) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Map<String, Object> data = new HashMap<>();
		String latitudeSeparated=deviceLocation.substring(0, deviceLocation.indexOf(","));
		String longitudeSeparated=deviceLocation.substring(deviceLocation.indexOf(",")+1,deviceLocation.length());
		String hash = GeoFireUtils.getGeoHashForLocation(new GeoLocation(Double.parseDouble(latitudeSeparated), Double.parseDouble(longitudeSeparated)));
        //data.put("device-id", deviceId);
        data.put("device-location-latitude", latitudeSeparated);
        data.put("device-location-longitude", longitudeSeparated);
		data.put("geohash", hash);
        data.put("device-location", deviceLocation);
        data.put("logged-in-username", getLoggedInUserName(context));
        db.collection("everything").document(deviceId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(aVoid -> Log.d("FIRE-STORE-UPSERT", "DocumentSnapshot successfully written!"))
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w("FIRE-STORE-UPSERT", "Error writing document", e);
                    }
                });
        Log.i("DONE!!!", "DATA persisted." + db.collection("everything").whereEqualTo("device-id", deviceId));
    }

    public void fetchLocationDeviceData(String deviceId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference docRef = db.collection("everything").document(deviceId);
        //db.collection("everything")
        //.whereEqualTo("device-id", deviceId)
        docRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document.exists()) {
                    Log.d("FIRE-STORE-FETCH", "DocumentSnapshot data: " + document.getData() + " => " + document.getData().get("device-location"));
                    String currentDeviceLocation = (String) document.getData().get("device-location");
                    Log.i("DONE!!!", "DATA FIRE-STORE-FETCH."+ currentDeviceLocation);
                }
            } else {
                Log.d("FIRE-STORE-FETCH", "Error getting documents: ", task.getException());
            }
        });
    }

    public List<String> fetchNearbyLocationDeviceData(String deviceLocation, Integer rangeDistance) {
        List<String> nearbyDevices = new ArrayList<>();
		FirebaseFirestore db = FirebaseFirestore.getInstance();
        // Find cities within 50km of London
        final GeoLocation center = new GeoLocation(Double.parseDouble(deviceLocation.substring(0, deviceLocation.indexOf(","))),
				Double.parseDouble(deviceLocation.substring(deviceLocation.indexOf(",")+1,deviceLocation.length())));
        final double radiusInM = rangeDistance * 1000;

		// Each item in 'bounds' represents a startAt/endAt pair. We have to issue
		// a separate query for each pair. There can be up to 9 pairs of bounds
		// depending on overlap, but in most cases there are 4.
        List<GeoQueryBounds> bounds = GeoFireUtils.getGeoHashQueryBounds(center, radiusInM);
        final List<Task<QuerySnapshot>> tasks = new ArrayList<>();
        for (GeoQueryBounds b : bounds) {
            Query q = db.collection("everything")
                    .orderBy("geohash")
                    .startAt(b.startHash)
                    .endAt(b.endHash);

            tasks.add(q.get());
        }

		// Collect all the query results together into a single list
        Tasks.whenAllComplete(tasks)
                .addOnCompleteListener(new OnCompleteListener<List<Task<?>>>() {
                    @Override
                    public void onComplete(@NonNull Task<List<Task<?>>> t) {
                        List<DocumentSnapshot> matchingDocs = new ArrayList<>();

                        for (Task<QuerySnapshot> task : tasks) {
                            QuerySnapshot snap = task.getResult();
                            for (DocumentSnapshot doc : snap.getDocuments()) {
                                Double lat = Double.parseDouble(doc.getString("device-location-latitude"));
                                Double lng = Double.parseDouble(doc.getString("device-location-longitude"));

                                // We have to filter out a few false positives due to GeoHash
                                // accuracy, but most will match
                                GeoLocation docLocation = new GeoLocation(lat, lng);
                                double distanceInM = GeoFireUtils.getDistanceBetween(docLocation, center);
                                if (distanceInM <= radiusInM) {
                                    matchingDocs.add(doc);
                                }
                            }
                        }Log.i("MATCHING_DOC", "BELOW");
                        matchingDocs.stream().forEach(element-> nearbyDevices.add(element.getString("logged-in-username")!=null?element.getString("logged-in-username"):element.getString("geohash")));
                        //matchingDocs.stream().forEach(element-> Log.i("STOPO!!", element.getString("logged-in-username")));
                        Log.i("STOPO", nearbyDevices.toString());
                    }
                });return  nearbyDevices;
    }

    public boolean isInside(int latitude_center, int longitude_center, int rad, int x, int y) {
        // Compare radius of circle with
        // distance of its center from
        // given point
        if ((x - latitude_center) * (x - latitude_center) +
                (y - longitude_center) * (y - longitude_center) <= rad * rad)
            return true;
        else
            return false;
    }

    // checking permission for storage and camera for writing and uploading images
    public void get_file_perm(Activity activity) {
        String[] perms = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA};

        //Checking for storage permission to write images for upload
        if (Constants.ASWP_FUPLOAD && Constants.ASWP_CAMUPLOAD && !check_permission(2, activity.getApplicationContext()) && !check_permission(3, activity.getApplicationContext())) {
            ActivityCompat.requestPermissions(activity, perms, Constants.file_perm);

            //Checking for WRITE_EXTERNAL_STORAGE permission
        } else if (Constants.ASWP_FUPLOAD && !check_permission(2, activity.getApplicationContext())) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, Constants.file_perm);

            //Checking for CAMERA permissions
        } else if (Constants.ASWP_CAMUPLOAD && !check_permission(3, activity.getApplicationContext())) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA}, Constants.file_perm);
        }
    }

    // using cookies to update user locations
    public String get_location(Context context) {
        String newloc = "0,0";
        //Checking for location permissions
        if (Constants.ASWP_LOCATION && (Build.VERSION.SDK_INT < 23 || check_permission(1, context))) {
            GPSTrack gps;
            gps = new GPSTrack(context);
            double latitude = gps.getLatitude();
            double longitude = gps.getLongitude();
            if (gps.canGetLocation()) {
                if (latitude != 0 || longitude != 0) {
                    if (Constants.true_online) {
                        set_cookie("lat=" + latitude);
                        set_cookie("long=" + longitude);
                        set_cookie("LATLANG=" + latitude + "x" + longitude);
                        Log.i("IMP", latitude + "---" + longitude);
                    }
                    //Log.d("SLOG_NEW_LOCATION", latitude + "," + longitude);  //enable to test dummy latitude and longitude
                    newloc = latitude + "," + longitude;
                } else {
                    Log.d("SLOG_UPDATED_LOCATION", "NULL");
                }
            } else {
                show_notification(1, 1, context);
                Log.d("SLOG_UPDATED_LOCATION", "FAIL");
            }
        }
        return newloc;
    }

    // get cookie value
    public String get_cookies(String cookie) {
        String value = "";
        if (Constants.true_online) {
            Constants.cookie_manager = CookieManager.getInstance();
            String cookies = Constants.cookie_manager.getCookie(Constants.ASWV_URL);
            if (cookies != null && !cookies.isEmpty()) {
                String[] temp = cookies.split(";");
                for (String ar1 : temp) {
                    if (ar1.contains(cookie)) {
                        String[] temp1 = ar1.split("=");
                        value = temp1[1];
                        break;
                    }
                }
            } else {
                Log.d("SLOG_COOKIES", "Cookies either NULL or Empty");
                value = "";
            }
        } else {
            Log.w("SLOG_NETWORK", "DEVICE NOT ONLINE");
        }
        return value;
    }

    @SuppressLint("ResourceAsColor")
    public boolean onCreateOptionsMenu(Menu menu, Activity context) {
        // Inflate the menu; this adds items to the action bar if it is present.
        context.getMenuInflater().inflate(R.menu.main, menu);
        SearchManager searchManager = (SearchManager) context.getSystemService(Context.SEARCH_SERVICE);
        final SearchView searchView = (SearchView) menu.findItem(R.id.menu_search).getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(context.getComponentName()));
        searchView.setQueryHint(context.getString(R.string.search_hint));
        searchView.setIconified(true);
        searchView.setIconifiedByDefault(true);
        searchView.clearFocus();

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            public boolean onQueryTextSubmit(String query) {
                searchView.clearFocus();
                aswm_view(Constants.ASWV_SEARCH + query, false, Constants.asw_error_counter, context.getApplicationContext());
                searchView.setQuery(query, false);
                return false;
            }

            public boolean onQueryTextChange(String query) {
                return false;
            }
        });
        //searchView.setQuery(Constants.asw_view.getUrl(),false);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item, Context context) {
        int id = item.getItemId();
        if (id == R.id.action_exit) {
            exit_app(context);
            return true;
        }
        return onOptionsItemSelected(item, context);
    }

    public boolean onNavigationItemSelected(MenuItem item, Context context) {
        int id = item.getItemId();
        if (id == R.id.nav_home) {
            aswm_view("file:///android_asset/offline.html", false, Constants.asw_error_counter, context);
        } else if (id == R.id.nav_doc) {
            aswm_view("https://github.com/mgks/Android-Constants/tree/master/documentation", false, Constants.asw_error_counter, context);
        } else if (id == R.id.nav_fcm) {
            aswm_view("https://github.com/mgks/Android-Constants/blob/master/documentation/fcm.md", false, Constants.asw_error_counter, context);
        } else if (id == R.id.nav_admob) {
            aswm_view("https://github.com/mgks/Android-Constants/blob/master/documentation/admob.md", false, Constants.asw_error_counter, context);
        } else if (id == R.id.nav_gps) {
            aswm_view("https://github.com/mgks/Android-Constants/blob/master/documentation/gps.md", false, Constants.asw_error_counter, context);
        } else if (id == R.id.nav_share) {
            aswm_view("https://github.com/mgks/Android-Constants/blob/master/documentation/share.md", false, Constants.asw_error_counter, context);
        } else if (id == R.id.nav_lay) {
            aswm_view("https://github.com/mgks/Android-Constants/blob/master/documentation/layout.md", false, Constants.asw_error_counter, context);
        } else if (id == R.id.nav_support) {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:hello@mgks.dev"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "SWV Help");
            context.startActivity(Intent.createChooser(intent, "Send Email"));
        }

        DrawerLayout drawer = ((Activity) context).findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    public String fcm_token() {
        final String[] fcm_token = {""};
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(instanceIdResult -> {
            fcm_token[0] = FirebaseMessaging.getInstance().getToken().getResult();
            if (!Constants.ASWP_OFFLINE) {
                set_cookie("FCM_TOKEN=" + fcm_token[0]);
                Log.d("SLOG_FCM_BAKED", "YES");
                //Log.d("SLOG_COOKIES", cookieManager.getCookie(ASWV_URL));
            }
            Log.d("SLOG_REQ_FCM_TOKEN", fcm_token[0]);
        }).addOnFailureListener(e -> Log.d("SLOG_REQ_FCM_TOKEN", "FAILED"));
        return fcm_token[0];
    }

    //Checking if particular permission is given or not
    public boolean check_permission(int permission, Context context) {
        switch (permission) {
            case 1:
                return ContextCompat.checkSelfPermission(context.getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

            case 2:
                return Build.VERSION.SDK_INT >= 30 || ContextCompat.checkSelfPermission(context.getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;

            case 3:
                return ContextCompat.checkSelfPermission(context.getApplicationContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;

        }
        return false;
    }

    //Creating image file for upload
    public File create_image(Context context) throws IOException {
        @SuppressLint("SimpleDateFormat")
        String file_name = new SimpleDateFormat("yyyy_mm_ss").format(new Date());
        String new_name = "file_" + file_name + "_";
        File sd_directory = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(new_name, ".jpg", sd_directory);
    }

    //Creating video file for upload
    public File create_video(Context context) throws IOException {
        @SuppressLint("SimpleDateFormat")
        String file_name = new SimpleDateFormat("yyyy_mm_ss").format(new Date());
        String new_name = "file_" + file_name + "_";
        File sd_directory = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(new_name, ".3gp", sd_directory);
    }

    //Launching app rating dialog [developed by github.com/hotchemi]
    public Runnable get_rating(Context context) {
        if (DetectConnection.isInternetAvailable(context)) {
            AppRate.with(context)
                    .setStoreType(StoreType.GOOGLEPLAY)     //default is Google Play, other option is Amazon App Store
                    .setInstallDays(Constants.ASWR_DAYS)
                    .setLaunchTimes(Constants.ASWR_TIMES)
                    .setRemindInterval()
                    .setTitle(R.string.rate_dialog_title)
                    .setMessage(R.string.rate_dialog_message)
                    .setTextLater(R.string.rate_dialog_cancel)
                    .setTextNever(R.string.rate_dialog_no)
                    .setTextRateNow(R.string.rate_dialog_ok)
                    .monitor();
            AppRate.showRateDialogIfMeetsConditions(context);
        }
        //for more customizations, look for AppRate and DialogManager
        return null;
    }

    //Creating custom notifications with IDs
    public void show_notification(int type, int id, Context context) {
        long when = System.currentTimeMillis();
        String cont_title = "", cont_text = "", cont_desc = "";

        Constants.asw_notification = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        Intent i = new Intent();
        if (type == 1) {
            i.setClass(context, MainActivity.class);
        } else if (type == 2) {
            i.setAction(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        } else {
            i.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.addCategory(Intent.CATEGORY_DEFAULT);
            i.setData(Uri.parse("package:" + context.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            i.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        }
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent;
        final int flag = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;
        pendingIntent = PendingIntent.getActivity(context, 0, i, flag);
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "");
        builder.setTicker(context.getString(R.string.app_name));
        switch (type) {
            case 1:
                cont_title = context.getString(R.string.loc_fail);
                cont_text = context.getString(R.string.loc_fail_text);
                cont_desc = context.getString(R.string.loc_fail_more);
                break;

            case 2:
                cont_title = context.getString(R.string.loc_perm);
                cont_text = context.getString(R.string.loc_perm_text);
                cont_desc = context.getString(R.string.loc_perm_more);
                builder.setSound(alarmSound);
                break;
        }
        builder.setContentTitle(cont_title);
        builder.setContentText(cont_text);
        builder.setStyle(new NotificationCompat.BigTextStyle().bigText(cont_desc));
        builder.setVibrate(new long[]{350, 700, 350, 700, 350});
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setOngoing(false);
        builder.setAutoCancel(true);
        builder.setWhen(when);
        builder.setContentIntent(pendingIntent);
        Constants.asw_notification_new = builder.build();
        Constants.asw_notification.notify(id, Constants.asw_notification_new);
    }

    //Printing pages
    private void print_page(WebView view, String print_name, boolean manual, Context context) {
        PrintManager printManager = (PrintManager) context.getSystemService(Context.PRINT_SERVICE);
        PrintDocumentAdapter printAdapter = view.createPrintDocumentAdapter(print_name);
        PrintAttributes.Builder builder = new PrintAttributes.Builder();
        builder.setMediaSize(PrintAttributes.MediaSize.ISO_A5);
        PrintJob printJob = printManager.print(print_name, printAdapter, builder.build());

        if (printJob.isCompleted()) {
            Toast.makeText(context, R.string.print_complete, Toast.LENGTH_LONG).show();
        } else if (printJob.isFailed()) {
            Toast.makeText(context, R.string.print_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void doWebViewPrint(String ss, Context context) {
        Constants.print_view.setWebViewClient(new WebViewClient() {

            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }

            //use Service Worker
            @Nullable
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                print_page(view, view.getTitle(), false, context);
                super.onPageFinished(view, url);
            }
        });
        // Generate an HTML document on the fly:
        Constants.print_view.loadDataWithBaseURL(null, ss, "text/html", "UTF-8", null);
    }

    public void exit_app(Context context) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    // Creating exit dialogue
    public void ask_exit(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        builder.setTitle(context.getString(R.string.exit_title));
        builder.setMessage(context.getString(R.string.exit_subtitle));
        builder.setCancelable(true);

        // Action if user selects 'yes'
        builder.setPositiveButton("Yes", (dialogInterface, i) -> exit_app(context));

        // Actions if user selects 'no'
        builder.setNegativeButton("No", (dialogInterface, i) -> {
        });

        // Create the alert dialog using alert dialog builder
        AlertDialog dialog = builder.create();

        // Finally, display the dialog when user press back button
        dialog.show();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        return false;
    }
}

