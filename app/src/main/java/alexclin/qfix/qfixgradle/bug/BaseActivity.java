package alexclin.qfix.qfixgradle.bug;

import android.app.Activity;

/**
 * BaseActivity
 *
 * @author alexclin  2017/1/14 14:12
 */

public abstract class BaseActivity extends Activity {
    protected void appInfoBase(StringBuilder builder){
        if(builder!=null) builder.append("\nbug msg from BaseActivity");
    }
}
