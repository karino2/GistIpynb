package karino2.livejournal.com.gistipynb;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonWriter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class UploaderActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_uploader);

        Intent intent = getIntent();
        if(intent != null) {
            String uristr = intent.getStringExtra("uri_arg");
            if(uristr == null) {
                showMessage("no uri arg. why?");
                finish();
                return;
            }
            // Basically, uri is written in prefs. So we do not need arg_uri.
            // I use it just because of checking.so I do not store it.

            Uri uri = Uri.parse(uristr);
            File file = new File(uri.getPath());

            ((EditText)findViewById(R.id.editTextFilePath)).setText(file.getName());
        }

        findViewById(R.id.buttonFillLast).setOnClickListener(v -> {
            ((EditText)findViewById(R.id.editTextUrl)).setText(getPrefs().getString("last_url", ""));
        });



    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_uploader, menu);
        return super.onCreateOptionsMenu(menu);
    }

    boolean duringPost = false;

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        ((MenuItem)menu.findItem(R.id.menu_item_post)).setEnabled(!duringPost);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_item_post:
                showMessage("Posting...");
                duringPost = true;
                invalidateOptionsMenu();
                postGist();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    void notifySendFinish() {
        duringPost = false;
        invalidateOptionsMenu();
    }

    String getTextFromET(int rid) {
       return ((EditText)findViewById(rid)).getText().toString();
    }

    SharedPreferences getPrefs() {
        return LoginActivity.getAppPreferences(this);
    }

    String getAccessToken() {
        return LoginActivity.getAccessTokenFromPreferences(getPrefs());
    }

    private void postGist() {
        String fileName = getTextFromET(R.id.editTextFilePath);
        String description = getTextFromET(R.id.editTextDescription);
        boolean isPublic = ((CheckBox)findViewById(R.id.checkBoxPublic)).isChecked();
        String updateUrl = getTextFromET(R.id.editTextUrl);

        String id;

        if(updateUrl.equals("")) {
            id = "";
        } else {
            id = Uri.parse(updateUrl).getLastPathSegment();
        }


        String accToken = getAccessToken();

        try {
            String content = getContent();

            String json = toJsonString(description, isPublic, fileName, content);



            JsonPoster poster = new JsonPoster(accToken, json, id, new OnContentReadyListener() {
                @Override
                public void onReady(String responseText) {
                    // should be notification.
                    Uri uri = Uri.parse(responseText);
                    String id = uri.getLastPathSegment();
                    String gistUrl = "https://gist.github.com/" + id;

                    // showMessage("url: " + gistUrl);
                    showUrlNotification(gistUrl);
                    notifySendFinish();
                    showMessage("Done");
                    finish();
                }

                @Override
                public void onFail(String message) {
                    showMessage("post fail: " + message);
                    notifySendFinish();
                }
            });
            poster.execute();
            // post json here.
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    final int NOTIFICATION_ID = 1;

    private void showUrlNotification(String url) {
        // .setSmallIcon(R.drawable.notification_icon)
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle("GistIpynb")
                        .setContentText(url);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        getPrefs().edit()
                .putString("last_url", url)
                .commit();

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        // builder.addAction(android.R.drawable.ic_menu_edit, "Copy", pendingIntent);
        builder.setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, builder.build());


    }

    String getUriArg() {
        return LoginActivity.getUriArgFromPreferences(getPrefs());
    }

    private String getContent() throws IOException {
        Uri uri = Uri.parse(getUriArg());
        InputStream is = null;
        try {
            is = getContentResolver().openInputStream(uri);
            return LoginActivity.readAll(is);
        }catch (FileNotFoundException e) {
            showMessage("file not found: " + e.getMessage());
            throw e;
        } finally {
            if(is != null)
                is.close();
        }
    }

    class JsonPoster extends AsyncTask<Object, String, Boolean> {
        OnContentReadyListener resultListener;
        String url;
        String accessToken;
        String jsonData;

        JsonPoster(String accToken, String jsonData, String id, OnContentReadyListener listener) {
            if(id.equals(""))
                url = "https://api.github.com/gists";
            else
                url = "https://api.github.com/gists/" + id;

            accessToken = accToken;
            this.jsonData = jsonData;
            this.resultListener = listener;
        }

        String responseText;
        String errorMessage = null;

        @Override
        protected Boolean doInBackground(Object... objects) {
            try {
                URL u = new URL(url);
                HttpURLConnection connection = (HttpURLConnection) u.openConnection();
                try {
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Authorization", "token " + accessToken);

                    connection.setUseCaches(false);
                    // connection.setDoInput(true);
                    connection.setDoOutput(true);

                    OutputStreamWriter ow = new OutputStreamWriter(connection.getOutputStream());
                    try {
                        ow.write(jsonData);
                        ow.flush();
                    } finally {
                        ow.close();
                    }


                    if (HttpURLConnection.HTTP_CREATED == connection.getResponseCode()) // new
                    {
                        responseText = connection.getHeaderField("Location");
                        return true;
                    }
                    if(HttpURLConnection.HTTP_OK == connection.getResponseCode()) // edit
                    {
                        responseText = url;
                        return true;
                    }

                    errorMessage = "Unknown response code: " + connection.getResponseCode();

                    return false;
                } finally {
                    connection.disconnect();
                }
            } catch (MalformedURLException e) {
                errorMessage = "MalformedURLException: " + e.getMessage();
                return false;
            } catch (IOException e) {
                errorMessage = "IOException: " + e.getMessage();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success)
                resultListener.onReady(responseText);
            else
                resultListener.onFail(errorMessage);
        }

    }

    void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    /*
    {
  "description": "the description for this gist",
  "public": true,
  "files": {
    "file1.txt": {
      "content": "String file contents"
    }
  }
     */

    String toJsonString(String description, boolean isPublic, String fileName, String fileContent) throws IOException {
        StringWriter sw = new StringWriter();
        JsonWriter writer = new JsonWriter(sw);
        writer.beginObject();

        writer.name("description").value(description)
                .name("public").value(isPublic);

        writer.name("files")
            .beginObject()
                .name(fileName) // TODO: need encode.
                .beginObject()
                .name("content").value(fileContent)
                .endObject()
            .endObject();

        writer.endObject();
        return sw.toString();
    }

}
