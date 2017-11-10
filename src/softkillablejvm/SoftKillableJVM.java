package softkillablejvm;

import java.io.IOException;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.MSG;
import com.sun.jna.platform.win32.WinUser.WNDCLASSEX;
import com.sun.jna.platform.win32.WinUser.WindowProc;

public class SoftKillableJVM {

	private final static SoftKillableJVM INSTANCE = new SoftKillableJVM();
	
	private static String className = null;
	private static String windowTitle = null;
	private static int exitStatus = 0;
	private static boolean isStarted = false;
	private static boolean isRunning = false;
	private static int lastError = 0;

	public static void start() {
		synchronized (INSTANCE) {
			if (isStarted) {
				throw new IllegalStateException();
			}
			isStarted = true;
		}
		if (className == null || className.isEmpty()) {
			className = SoftKillableJVM.class.getName() + "_class";
		}
		if (windowTitle == null || windowTitle.isEmpty()) {
			windowTitle = SoftKillableJVM.class.getName();
		}
		new Thread() {
			public void run() {
				new MessagePump();
			}
		}.start();
	}

	public static void setClassName(final String className) {
		synchronized (INSTANCE) {
			if (isStarted) {
				throw new IllegalStateException();
			}
			SoftKillableJVM.className = className;
		}
	}

	public static void setWindowTitle(final String windowTitle) {
		synchronized (INSTANCE) {
			if (isStarted) {
				throw new IllegalStateException();
			}
			SoftKillableJVM.windowTitle = windowTitle;
		}
	}

	public static void setExitStatus(final int exitStatus) {
		synchronized (INSTANCE) {
			if (isStarted) {
				throw new IllegalStateException();
			}
			SoftKillableJVM.exitStatus = exitStatus;
		}
	}
	
	public static boolean isStarted() {
		return SoftKillableJVM.isStarted;
	}
	
	public static boolean isRunning() {
		return SoftKillableJVM.isRunning;
	}
	
	public static int getLastError() {
		return lastError;
	}

	private static class MessagePump implements WindowProc {

		@Override
		public LRESULT callback(HWND hwnd, int uMsg, WPARAM wParam, LPARAM lParam) {
			switch (uMsg) {
			case WinUser.WM_CREATE:
				isRunning = true;
				return new LRESULT(0);
			case WinUser.WM_CLOSE:
				System.exit(SoftKillableJVM.exitStatus);
			case WinUser.WM_DESTROY:
				return new LRESULT(0);
			default:
				return User32.INSTANCE.DefWindowProc(hwnd, uMsg, wParam, lParam);
			}
		}

		public MessagePump() {
			WNDCLASSEX wcex = new WNDCLASSEX();
			wcex.hInstance = Kernel32.INSTANCE.GetModuleHandle(null);
			wcex.lpfnWndProc = MessagePump.this;
			wcex.lpszClassName = SoftKillableJVM.className;
			User32.INSTANCE.RegisterClassEx(wcex);
			if (User32.INSTANCE.RegisterClassEx(wcex) == null) {
				lastError = Kernel32.INSTANCE.GetLastError();
				return;
			}
			HWND hwnd = User32.INSTANCE.CreateWindowEx(0, SoftKillableJVM.className,
					SoftKillableJVM.windowTitle, User32.WS_OVERLAPPED, 0, 0, 0, 0, null, null, wcex.hInstance,
					null);
			if (hwnd == null) {
				lastError = Kernel32.INSTANCE.GetLastError();
				User32.INSTANCE.UnregisterClass(wcex.lpszClassName, wcex.hInstance);
				return;
			}
			MSG msg = new MSG();
			while (User32.INSTANCE.GetMessage(msg, hwnd, 0, 0) != 0) {
				User32.INSTANCE.TranslateMessage(msg);
				User32.INSTANCE.DispatchMessage(msg);
			}
			User32.INSTANCE.UnregisterClass(wcex.lpszClassName, wcex.hInstance);
			isRunning = false;
			isStarted = false;
		}
	};
	
	private SoftKillableJVM() {
		// nothing to do
	}

	public static void main(String[] args) {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				System.out.println("shutdown");
			}
		});
		
		new Thread() {
			public void run() {
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				int pid = Kernel32.INSTANCE.GetCurrentProcessId();
				try {
					Runtime.getRuntime().exec("taskkill -pid " + pid);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}.start();

		SoftKillableJVM.start();
		
		for (;;) {
			System.out.print('.');
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
