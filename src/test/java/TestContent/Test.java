package TestContent;

import httpServer.booter;
import nlogger.nlogger;

public class Test {
	public static void main(String[] args) {
		booter booter = new booter();
		try {
			System.out.println("SYJJContent");
			System.setProperty("AppName", "SYJJContent");
			booter.start(1006);
		} catch (Exception e) {
			nlogger.logout(e);
		}
	}
}
