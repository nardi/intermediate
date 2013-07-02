package nl.nardilam.intermediate;

import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

public interface BaseGameObject extends Drawable, Updateable, Touchable {
	public BaseGameObject getParent();
	void setParent(GameObject parent);
	public GameObject getObjectParent();
	void setParent(GameFragment parentFragment);
	public GameFragment getFragmentParent();
	void orphanize();
	public void addObject(BaseGameObject go);
	public void addObject(BaseGameObject go, int index);
	public int removeObject(BaseGameObject go);
	public void detach();
	public void swapFor(BaseGameObject go);
	public void update(long dt);
	public void draw(Canvas canvas);
	public boolean touch(View v, MotionEvent me);
	public void attach();
	public void run();
	public void halt();
	public void screenChange(int width, int height);
}
