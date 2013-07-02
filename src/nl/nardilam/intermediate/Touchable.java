package nl.nardilam.intermediate;

import android.view.MotionEvent;
import android.view.View;

public interface Touchable extends View.OnTouchListener {
	public boolean touch(View v, MotionEvent me);
}
