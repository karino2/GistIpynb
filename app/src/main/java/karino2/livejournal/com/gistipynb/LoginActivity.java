package karino2.livejournal.com.gistipynb;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by _ on 2017/01/30.
 */

public class LoginActivity extends AppCompatActivity {
    WebView webView;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.activity_login);



        webView = (WebView)findViewById(R.id.webview);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setBlockNetworkImage(false);
        webView.getSettings().setLoadsImagesAutomatically(true);

        webView.setWebViewClient(new WebViewClient(){

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if(url.startsWith(getString(R.string.redirect_uri))) {
                    String code = Uri.parse(url).getQueryParameter("code");
                    if(code != null) {
                        getAccessToken(code);
                        return true;
                    }

                }
                return super.shouldOverrideUrlLoading(view, url);
            }
        });

        checkValidTokenAndGotoTopIfValid();

    }


    private String getAuthorizeUrl() {
        return "https://github.com/login/oauth/authorize?client_id=" +
                getString(R.string.client_id) + "&scope=user,gist&redirect_uri=" + getString(R.string.redirect_uri);
    }

    private void getAccessToken(String code) {
        (new GetAccessTokenTask(code, new OnContentReadyListener() {
            @Override
            public void onReady(String responseText) {
                // auth token. store it here and goto next activity.
                getSettingsPrefs()
                        .edit()
                        .putString("access_token", responseText)
                        .commit();
                gotoTopActivity();
            }

            @Override
            public void onFail(String message) {
                showMessage("getAuthTask fail: " + message);
                Log.d("GistIpynb", "getAuthTask fail: " + message);
            }
        })).execute();
    }

    private SharedPreferences getSettingsPrefs() {
        return getSharedPreferences("settings", MODE_PRIVATE);
    }


    void checkValidTokenAndGotoTopIfValid() {
        String accToken = getSettingsPrefs().getString("access_token", "");
        if(accToken.equals("")) {
            // not valid.
            webView.loadUrl(getAuthorizeUrl());
            return;
        }

        (new CheckTokenValidity(accToken, new OnContentReadyListener() {
            @Override
            public void onReady(String responseText) {
                // valid!
                gotoTopActivity();
            }

            @Override
            public void onFail(String message) {
                // invalid token. need to get token again.
                webView.loadUrl(getAuthorizeUrl());
            }
        })).execute();


    }

    private void gotoTopActivity() {
        showMessage("gotoTopActivity: NYI");
    }

    void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }


    public interface OnContentReadyListener {
        void onReady(String responseText);
        void onFail(String message);
    }

    class CheckTokenValidity extends AsyncTask<Object, String, Boolean> {
        OnContentReadyListener resultListener;
        String url = "https://api.github.com/gists";
        String accessToken;

        CheckTokenValidity(String accessToken, OnContentReadyListener listener) {
            resultListener = listener;
            this.accessToken = accessToken;
        }

        String responseText = "success";
        String errorMessage = null;

        @Override
        protected Boolean doInBackground(Object... objects) {
            try {
                URL u = new URL(url);
                HttpURLConnection connection = (HttpURLConnection)u.openConnection();
                try {

                    if(HttpURLConnection.HTTP_OK == connection.getResponseCode())
                        return true;

                    return false;
                }
                finally {
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

    class GetAccessTokenTask extends AsyncTask<Object, String, Boolean> {

        OnContentReadyListener resultListener;
        String url;

        GetAccessTokenTask(String code, OnContentReadyListener listener) {
            url =  "https://github.com/login/oauth/access_token" +
                    "?client_id=" + getString(R.string.client_id) +
                    "&client_secret=" + getString(R.string.client_secret) +
                    "&code=" + code;
            resultListener = listener;
        }

        String responseText = null;
        String errorMessage = null;

        String readBody(InputStream is) throws IOException {
            BufferedReader reader;
            reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder builder = new StringBuilder();
            String line = reader.readLine();
            boolean first = true;
            while (line != null) {
                if (!first)
                    builder.append("\n");
                first = false;
                builder.append(line);
                line = reader.readLine();
            }
            return builder.toString();
        }


        class AuthenticationJson {
            @SerializedName("access_token")
            public String accessToken;
            @SerializedName("token_type")
            public String tokenType;
            public String scope;
        }

        @Override
        protected Boolean doInBackground(Object... params) {
            try {
                URL u = new URL(url);
                HttpURLConnection connection = (HttpURLConnection)u.openConnection();
                try {

                    connection.setRequestProperty("Accept", "application/json");
                    connection.setUseCaches(false);
                    connection.setDoInput(true);
                    connection.setDoOutput(true);

                    String body = readBody(connection.getInputStream());
                    Log.d("GistIpynb", body);

                    Gson gson = new Gson();

                    responseText = gson.fromJson(body, AuthenticationJson.class).accessToken;


                    /*
                    String header = connection.getHeaderField("Set-Cookie");

                    if(HttpURLConnection.HTTP_MOVED_TEMP != connection.getResponseCode() ||
                            header.length() == 0)
                    {
                        return false;
                    }
                    responseText = header;
                    */
                    return true;
                }
                finally {
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

}
