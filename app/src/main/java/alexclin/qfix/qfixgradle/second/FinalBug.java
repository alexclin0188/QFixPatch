package alexclin.qfix.qfixgradle.second;

import java.util.Date;

import alexclin.qfix.qfixgradle.bug.Bug111;

/**
 * FinalBug
 *
 * @author alexclin  2017/1/14 18:22
 */

public final class FinalBug extends Bug111 {

    protected void appendBugMsg(StringBuilder builder) {
        if (builder != null){
            super.appendBugMsg(builder);
            builder.append("\nbug msg from FinalBug--------");
        }
    }

    public String getTestFinalBugMsg(){
        StringBuilder builder = new StringBuilder();
        builder.append(new Date());
        appendBugMsg(builder);
        return builder.toString();
    }
}
