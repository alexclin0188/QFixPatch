package alexclin.qfix.qfixgradle.bug;

/**
 * TestActivity1
 *
 * @author alexclin  2017/1/14 16:03
 */

public abstract class TestActivity1 extends BaseActivity{

    protected void appInfo1(StringBuilder builder){
        if(builder!=null) builder.append("\nbug msg from TestActivity1");
    }
}
