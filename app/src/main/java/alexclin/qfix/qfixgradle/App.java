package alexclin.qfix.qfixgradle;

import android.content.Context;
import android.os.Environment;
import android.support.multidex.MultiDex;

import java.io.File;

import alexclin.patch.qfix.app.PatchApplication;
import alexclin.patch.qfix.tool.PatchTool;

/**
 * App
 *
 * @author alexclin  2017/1/14 13:26
 */

public class App extends PatchApplication {
    private static final String DELEGATE_NAME = "alexclin.qfix.qfixgradle.AppDelegate";

    public App() {
        super(DELEGATE_NAME);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(base);
        File debugPatch = new File(Environment.getExternalStorageDirectory(),"debugPatch.apk");
        PatchTool.installPatch(this,debugPatch);
    }
}
