package karino2.livejournal.com.gistipynb;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

public class CopyUrlReceiver extends BroadcastReceiver {
    public CopyUrlReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        ClipboardManager clipboard = (ClipboardManager)
                context.getSystemService(Context.CLIPBOARD_SERVICE);
        String uriString = intent.getStringExtra("result_url");
        // ClipData clip = ClipData.newRawUri("result", Uri.parse(uriString));
        ClipData clip = ClipData.newPlainText("result", uriString);
        clipboard.setPrimaryClip(clip);

        Toast.makeText(context, "Copied: " + uriString, Toast.LENGTH_SHORT).show();
    }
}
