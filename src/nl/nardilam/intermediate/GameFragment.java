package nl.nardilam.intermediate;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import android.support.v4.app.Fragment;
import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

/*
 * Based on code from http://www.mysecretroom.com/www/programming-and-software/android-game-loops
 */

public class GameFragment extends Fragment
	implements SurfaceHolder.Callback, BaseGameObject {
	private final static int MAX_FRAME_SKIPS = 5;

	private final GameFragment fragment = this;
	
	private GameThread thread = new GameThread();
	private boolean running = false;
	private SurfaceHolder surfaceHolder = null;
	/*
	 * surfaceCreated is true when the surface actually exists
	 * (for thread safety).
	 */
	private boolean surfaceCreated = false;
	/*
	 * paused is used to indicate a halt (pause) caused by the OS,
	 * which means the thread should be restarted automatically.
	 */
	private boolean paused = false;

	private int targetFps;
	private float updatePeriod;
	public boolean showStats = false;
	public boolean alwaysRecieveEvents = false;
	
	private Paint statsPaint = new Paint();
	private long beginTime, timeDiff, sleepTime, updateTime,
		updateCount, drawCount, gameStartTime;

	{
		setTargetFps(60);
		
		statsPaint.setColor(Color.WHITE);
		statsPaint.setStyle(Style.FILL_AND_STROKE);
		statsPaint.setStrokeWidth(0);
		statsPaint.setTextSize(30);
	}
	
	public void setTargetFps(int fps) {
		targetFps = fps;
		updatePeriod = 1000f / targetFps;
	}
	
	public long getTotalTime() {
		if (gameStartTime == 0)
			return 0;
		return SystemClock.uptimeMillis() - gameStartTime;
	}
	
	public boolean isRunning() {
		return running;
	}
	
	public Paint getStatsPaint() {
		return statsPaint;
	}

	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		SurfaceView surface = new SurfaceView(this.getActivity());
		surface.getHolder().addCallback(this);
		surface.setFocusable(true);
		surface.setOnTouchListener(thread);
		return surface;
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		surfaceHolder = holder;
		
		Canvas c = holder.lockCanvas();
		thread.draw(c);
		holder.unlockCanvasAndPost(c);
		
		surfaceCreated = true;
		
		if (thread.waiting())
			thread.shouldWait(false);
	}

	public synchronized void start() {
		if (!running) {
			this.run();
			running = true;
			if (thread.getState() != Thread.State.NEW) {
				thread = new GameThread();
				getView().setOnTouchListener(thread);
			}
			thread.start();
		}
	}

	public synchronized void stop() {
		if (running) {
			halt();
			while (true) {
				try {
					thread.join();
					return;
				} catch (InterruptedException e) { }
			}
		}
	}
	
	private class GameThread extends Thread implements View.OnTouchListener {
		private boolean m_waiting = false;
		private boolean m_shouldWait = false;
		private Object waitLock = new Object();
		
		public boolean waiting() {
			synchronized (waitLock) { return m_waiting; }
		}
		
		public void shouldWait(boolean b) {
			synchronized (waitLock) { m_shouldWait = b; }
		}			
		
		private long prevUpdate;
		
		private synchronized void update() {
			if (prevUpdate == 0)
				prevUpdate = SystemClock.uptimeMillis();
			long dt = SystemClock.uptimeMillis() - prevUpdate;
			fragment.update(dt);
			prevUpdate += dt;
		}
		
		private void drawStats(Canvas canvas) {
			canvas.drawText("Last update time: " + updateTime, 5, canvas.getHeight() - 75, statsPaint);
			canvas.drawText("Update count: " + updateCount, 5, canvas.getHeight() - 40, statsPaint);
			canvas.drawText("Skipped frames: " + (updateCount - drawCount), 5, canvas.getHeight() - 5, statsPaint);
		}

		private synchronized void draw(Canvas canvas) {
			fragment.draw(canvas);
			if (showStats)
				drawStats(canvas);
		}
		
		Queue<Runnable> eventQueue = new LinkedList<Runnable>();
		
		@Override
		public void run() {
			int framesSkipped;
			
			if (gameStartTime == 0)
				gameStartTime = SystemClock.uptimeMillis();
			
			while (running) {
				if (m_shouldWait) {
					m_waiting = true;
					while (m_shouldWait);
					m_waiting = false;
				}
				
				framesSkipped = 0;
				
				for (Runnable r : eventQueue)
					r.run();
				
				beginTime = SystemClock.uptimeMillis();
				update();
				
				if (surfaceCreated) {
					Canvas canvas = null;
					try {
						canvas = surfaceHolder.lockCanvas();
						synchronized (surfaceHolder) {
							if (canvas != null) {
								draw(canvas);
							}
						}
					} finally {
						if (canvas != null)
							surfaceHolder.unlockCanvasAndPost(canvas);
					}
				}

				timeDiff = SystemClock.uptimeMillis() - beginTime;
				sleepTime = (long)(updatePeriod - timeDiff);
				
				if (sleepTime > 0)
					SystemClock.sleep(sleepTime);
				
				while (sleepTime < 0 && framesSkipped < MAX_FRAME_SKIPS) {
					update();
					sleepTime += updatePeriod;
					framesSkipped++;
				}
				
				updateTime = (SystemClock.uptimeMillis() - beginTime) / (framesSkipped + 1);
				updateCount += framesSkipped + 1;
				drawCount++;
				
				//Log.d("GameFragment", "Game time = " + getTotalTime());
			}
		}

		@Override
		public synchronized boolean onTouch(final View v, final MotionEvent me) {
			if (running || alwaysRecieveEvents) {
				eventQueue.add(new Runnable() {
					public void run() {
						fragment.touch(v, me);
					}
				});
				return true;
			}
			return false;
		}
	}
	
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		screenChange(width, height);
	}
	
	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		surfaceCreated = false;
		/*
		 * To stop the thread from accessing the surface, you can either shut it down
		 * or block it after completing the current update-draw cycle.
		 * It seems to be about equal in performance.
		 */
		/* thread.shouldWait(true); 	// Block thread
		while (!thread.waiting()); */
		this.halt(); 					// Shut down thread
		surfaceHolder = null;
    }
	
	@Override
	public void onPause() {
		super.onPause();

		if (running) {
			this.halt();
			paused = true;
		}
	}
	
	@Override
	public void onResume() {
		super.onResume();

		if (paused) {
			this.run();
			paused = false;
		}
	}
	
	/* GameObject code, copied from GameObject.java */
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
