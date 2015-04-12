package martin.app.bitunion;

import android.app.Application;

import martin.app.bitunion.util.BUApi;
import martin.app.bitunion.util.Settings;

public class BUApp extends Application {
    private static BUApp instance;

    // 静态变量在整个应用中传递网络连接参数，包括session, username, password信息
    public static Settings settings = new Settings();

    @Override
    public void onCreate() {
        instance = this;
        super.onCreate();

        settings.readPreference(this);
        BUApi.init(this);
    }

    public static BUApp getInstance() {
        return instance;
    }
}