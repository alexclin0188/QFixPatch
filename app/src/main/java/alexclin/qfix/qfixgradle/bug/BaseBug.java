package alexclin.qfix.qfixgradle.bug;

/**
 * BaseBug
 *
 * @author alexclin  2017/1/14 14:12
 */

public abstract class BaseBug {
    protected void appendBugMsg(StringBuilder builder) {
        if (builder != null) builder.append("\nbug msg from BaseBug--------");
    }
}
