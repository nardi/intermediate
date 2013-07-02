package nl.nardilam.intermediate;


import java.util.ArrayList;
import java.util.List;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

public class GameObject implements BaseGameObject {
	private GameFragment parentFragment = null;
	private GameObject parent = null;
	private List<BaseGameObject> childObjects = new ArrayList<BaseGameObject>();
	private boolean iterating = false;
	/*
	 * You can't change a List while iterating over it, so a record is kept
	 * instead and we perform these changes later.
	 */
	private SparseArray<BaseGameObject> objectsToAdd = new SparseArray<BaseGameObject>();
	private int listSizeToBe = 0;
	private List<BaseGameObject> objectsToRemove = new ArrayList<BaseGameObject>();
	
	public BaseGameObject getParent() {
		if (parent == null)
			return parentFragment;
		return parent;
	}

	public void setParent(GameObject parent) {
		this.parentFragment = parent == null ? null : parent.getFragmentParent();
		this.parent = parent;
	}
	
	public GameObject getObjectParent() {
		return parent;
	}
	
	public void setParent(GameFragment parentFragment) {
		this.parentFragment = parentFragment;
		this.parent = null;
	}
	
	public GameFragment getFragmentParent() {
		return parentFragment;
	}
	
	public void orphanize() {
		this.parentFragment = null;
		this.parent = null;
	}

	public void addObject(BaseGameObject go) {
		addObject(go, listSizeToBe);
	}
	
	public void addObject(BaseGameObject go, int index) {
		go.setParent(this);
		go.attach();
		if (iterating) {
			objectsToAdd.put(index, go);
			listSizeToBe++;
		}
		else {
			childObjects.add(index, go);
			listSizeToBe = childObjects.size();
		}
	}

	public int removeObject(BaseGameObject go) {
		int index = childObjects.indexOf(go);
		if (iterating) {
			listSizeToBe--;
			objectsToRemove.add(go);
		}
		else {
			childObjects.remove(go);
			listSizeToBe = childObjects.size();
		}
		if (go.getParent() == this)
			go.orphanize();
		return index;
	}
	
	public void detach() { 
		if (getParent() != null) {
			getParent().removeObject(this);
			orphanize();
		}
	}
	
	public void swapFor(BaseGameObject go) {
		BaseGameObject parent = getParent();
		if (parent != null) {
			int index = parent.removeObject(this);
			parent.addObject(go, index);
		}
	}
	
	private void commitChanges() {
		if (objectsToAdd.size() != 0) {
			for (int i = 0; i < objectsToAdd.size(); i++) {
				int index = objectsToAdd.keyAt(i);
				BaseGameObject go = objectsToAdd.valueAt(i);
				childObjects.add(index, go);
			}
			objectsToAdd.clear();
		}
		
		if (!objectsToRemove.isEmpty()) {
			for (BaseGameObject go : objectsToRemove)
				childObjects.remove(go);
			objectsToRemove.clear();
		}
	}
	
	protected void preUpdate(long dt) {}
	protected void onUpdate(long dt) {}
	protected void postUpdate(long dt) {}
	
	public final void update(long dt) {
		preUpdate(dt);
		onUpdate(dt);
		iterating = true;
		for (BaseGameObject go : childObjects)
			go.update(dt);
		iterating = false;
		commitChanges();
		postUpdate(dt);
	}

	protected void preDraw(Canvas canvas) {}
	protected void onDraw(Canvas canvas) {}
	protected void postDraw(Canvas canvas) {}
	
	@SuppressLint("WrongCall")
	public final void draw(Canvas canvas) {
		preDraw(canvas);
		onDraw(canvas);
		iterating = true;
		for (BaseGameObject go : childObjects)
			go.draw(canvas);
		iterating = false;
		commitChanges();
		postDraw(canvas);
	}
	
	public boolean onTouch(View v, MotionEvent me) {
		return false;
	}
	
	public final boolean touch(View v, MotionEvent me) {
		boolean eventUsed = false;
		eventUsed |= onTouch(v, me);
		iterating = true;
		for (BaseGameObject go : childObjects)
			eventUsed |= go.touch(v, me);
		iterating = false;
		commitChanges();
		return eventUsed;
	}
	
	protected void onAttach() {}
	
	public final void attach() {
		onAttach();
		iterating = true;
		for (BaseGameObject go : childObjects)
			go.attach();
		iterating = false;
		commitChanges();
	}
	
	protected void onRun() {}
	
	public final void run() {
		onRun();
		iterating = true;
		for (BaseGameObject go : childObjects)
			go.run();
		iterating = false;
		commitChanges();
	}
	
	protected void onHalt() {}
	
	public final void halt() {
		onHalt();
		iterating = true;
		for (BaseGameObject go : childObjects)
			go.halt();
		iterating = false;
		commitChanges();
	}
	
	protected void onScreenChange(int width, int height) {}
	
	public final void screenChange(int width, int height) {
		onScreenChange(width, height);
		iterating = true;
		for (BaseGameObject go : childObjects)
			go.screenChange(width, height);
		iterating = false;
		commitChanges();
	}
}
