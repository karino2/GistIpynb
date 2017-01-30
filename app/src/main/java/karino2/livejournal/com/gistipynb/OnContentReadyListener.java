package karino2.livejournal.com.gistipynb;

/**
 * Created by _ on 2017/01/30.
 */
public interface OnContentReadyListener {
    void onReady(String responseText);

    void onFail(String message);
}
