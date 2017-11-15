package interfaceApplication;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.axis.encoding.ser.TimeSerializer;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.jsoup.Jsoup;

import com.mysql.fabric.xmlrpc.base.Param;
import com.sun.star.awt.EndDockingEvent;
import com.sun.star.io.TempFile;

import JGrapeSystem.rMsg;
import apps.appsProxy;
import authority.authObject;
import check.checkHelper;
import database.DBHelper;
import database.db;
import httpClient.request;
import httpServer.grapeHttpUnit;
import interfaceController.interfaceUnit;
import interfaceModel.GrapeDBSpecField;
import interfaceModel.GrapeTreeDBModel;
import io.netty.handler.codec.http.HttpContentEncoder.Result;
import nlogger.nlogger;
import rpc.execRequest;
import security.codec;
import string.StringHelper;
import time.TimeHelper;

public class Content {
	private DBHelper content = new DBHelper("mongodb", "cObject");
	private String appid = appsProxy.appidString();
	private String ApiHost = "";

	public Content() {
		JSONObject object = appsProxy.configValue();
		if (object != null && object.size() > 0) {
			if (object.containsKey("other")) {
				object = JSONObject.toJSON(object.getString("other"));
				if (object != null && object.size() > 0) {
					ApiHost = object.getString("APIHost");
				}
			}
		}
	}

	private db bind() {
		return content.bind(appid);
	}

	public String SetInfo(String appids, String insid, String wbName, String column) {
		String info = null;
		JSONObject object = JSONObject.toJSON(execRequest.getChannelValue(grapeHttpUnit.formdata).toString());
		info = object.getString("param");
		info = codec.DecodeFastJSON(info);
		System.out.println("info: " + info);
		String wbid = getWbid(appids, wbName); // 网站id
		String ogid = getOgid(appids, wbid, column); // 栏目id
		if (StringHelper.InvaildString(wbid) && StringHelper.InvaildString(ogid) && StringHelper.InvaildString(info)) {
			object = JSONObject.toJSON(info);
			if (object != null && object.size() > 0) {
				switch (column) {
				case "今日关注":
					object = getTime(object); // 解析时间字段
					break;
				case "国企党建":
					object = getTimes(object); // 解析时间字段
					break;
				case "工作动态":
					object = getTimeByJwgk(object); // 解析时间字段
					break;
				case "政策法规":
					object = getTimeByzcfg(object); // 解析时间字段
					break;
				}
			}
		}
		return SetInfo(appids, insid, wbid, ogid, (object != null && object.size() > 0) ? object.toJSONString() : null);
	}

	/**
	 * 将采集来的数据存入数据库
	 * 
	 * @project SYJJContent
	 * @package interfaceApplication
	 * @file Content.java
	 * 
	 * @return
	 *
	 */
	@SuppressWarnings("unchecked")
	public String SetInfo(String appids, String insid, String wbid, String ogid, String info) {
		String mainName = "", content = "";
		JSONObject object = JSONObject.toJSON(info);
		String result = rMsg.netMSG(100, "导入数据失败");
		if (object != null && object.size() > 0) {
			if (object.containsKey("mainName")) {
				mainName = object.getString("mainName");
			}
			if (object.containsKey("content")) {
				content = object.get("content").toString();
				content = codec.DecodeHtmlTag(content);
				content = codec.decodebase64(content);
			}
			if (isExsist(appids, ogid, wbid, mainName, content)) {
				return result;
			}
			object.put("wbid", wbid);
			object.put("ogid", ogid);
			object.put("slevel", 0);
			object.put("state", 2);
			object = dencode(object);
			String resultString = AddContent(appids, object);
			object = JSONObject.toJSON(resultString);
			if (object != null && object.size() > 0) {
				if (Long.parseLong(object.getString("errorcode")) == 0) {
					result = rMsg.netMSG(0, "导入数据成功");
				}
			}
		}
		return result;
	}

	/**
	 * 获取栏目id
	 * 
	 * @project SYJJContent
	 * @package interfaceApplication
	 * @file Content.java
	 * 
	 * @param appids
	 * @param webName
	 * @return
	 *
	 */
	private String getWbid(String appids, String webName) {
		DBHelper helper = new DBHelper("mongodb", "websiteList");
		db db = helper.bind(appids);
		if (!checkHelper.isNum(webName)) {
			JSONObject object = db.eq("title", webName).find();
			webName = (object != null && object.size() > 0) ? object.getMongoID("_id") : null;
		}
		return webName;
	}

	//
	// @SuppressWarnings("unchecked")
	// public String SetInfo(String appids, String insid, String wbid, String
	// column, String info) {
	// String ogid = null;
	// String mainName = "";
	// String result = rMsg.netMSG(100, "导入数据失败");
	// ogid = getOgid(appids, column);
	// if (StringHelper.InvaildString(ogid)) {
	// info = codec.DecodeFastJSON(info);
	// JSONObject object = JSONObject.toJSON(info);
	// if (object != null && object.size() > 0) {
	// if (object.containsKey("mainName")) {
	// mainName = object.getString("mainName");
	// }
	// if (isExsist(appids, ogid, wbid, mainName)) {
	// return result;
	// }
	// object.put("wbid", wbid);
	// object.put("ogid", ogid);
	// object = dencode(object);
	// if (column.equals("今日关注")) {
	// object = getTime(object);
	// }
	// if (column.equals("国企党建")) {
	// object = getTimes(object);
	// }
	// String resultString = AddContent(appids, object);
	// object = JSONObject.toJSON(resultString);
	// if (object != null && object.size() > 0) {
	// if (Long.parseLong(object.getString("errorcode")) == 0) {
	// result = rMsg.netMSG(0, "导入数据成功");
	// }
	// }
	// }
	// }
	// return result;
	// }

	/**
	 * 获取今日关注时间及作者
	 * 
	 * @project SYJJContent
	 * @package interfaceApplication
	 * @file Content.java
	 * 
	 * @param object
	 * @return
	 *
	 */
	@SuppressWarnings("unchecked")
	private JSONObject getTime(JSONObject object) {
		if (object != null && object.size() > 0) {
			String time = object.getString("time");
			time = time.split("责任编辑:")[0].trim();
			String author = time.split("责任编辑:")[1].trim();
			object.put("time", time);
			object.put("author", author);
		}
		return object;
	}

	/**
	 * 获取国企党建时间及来源
	 * 
	 * @project SYJJContent
	 * @package interfaceApplication
	 * @file Content.java
	 * 
	 * @param object
	 * @return
	 *
	 */
	@SuppressWarnings("unchecked")
	private JSONObject getTimes(JSONObject object) {
		if (object != null && object.size() > 0) {
			String time = object.getString("time");
			time = time.split(" 　　 ")[1].trim().split("：")[1];
			String souce = time.split(" 　　 ")[0].trim().split("：")[1];
			object.put("time", time);
			object.put("souce", souce);
		}
		return object;
	}

	/**
	 * 获取居务公开下工作动态栏目的时间，作者，来源，阅读次数
	 * 
	 * @project SYJJContent
	 * @package interfaceApplication
	 * @file Content.java
	 * 
	 * @param object
	 * @return
	 *
	 */
	private JSONObject getTimeByJwgk(JSONObject object) {
		if (object != null && object.size() > 0) {
			String data = object.getString("time").trim();
			// 捕获发布日期
			String tempTime = catchString("发布日期：", " ", data);
			// 捕获发布单位
			String tempAuthor = catchString("发布单位：", " ", data);
			// 捕获来源
			String tempSource = catchString("来源：", " ", data);

			object.puts("time", tempTime).puts("author", tempAuthor).puts("souce", tempSource);
		}
		return object;
	}

	private static String catchString(String caption, String endChr, String data) {
		int l = caption.length();
		int end, i = data.toLowerCase().indexOf(caption);
		String temp = "", tempResult = "";
		if (i >= 0) {
			i += l;
			temp = data.substring(i).trim();
			end = temp.indexOf(endChr);
			if (end >= 0) {
				tempResult = temp.substring(0, end).trim();
			}
		}
		return tempResult;
	}

	/**
	 * 获取政策法规时间，作者
	 * 
	 * @project SYJJContent
	 * @package interfaceApplication
	 * @file Content.java
	 * 
	 * @param object
	 * @return
	 *
	 */
	@SuppressWarnings("unchecked")
	private JSONObject getTimeByzcfg(JSONObject object) {
		if (object != null && object.size() > 0) {
			String data = object.getString("time").trim();
			String time = data.split("作者：")[0].trim();
			String author = data.split("作者：")[1].trim();
			object.put("time", time);
			object.put("author", author);
		}
		return object;
	}

	@SuppressWarnings("unchecked")
	private String AddContent(String appids, JSONObject object) {
		String mainName = "";
		GrapeTreeDBModel model = new GrapeTreeDBModel();
		GrapeDBSpecField gDbSpecField = new GrapeDBSpecField();
		JSONObject obj = appsProxy.tableConfig("cawContent");
		gDbSpecField.importDescription(obj);
		model.descriptionModel(gDbSpecField);
		model.bind(appids);

		System.out.println("obj: " + object);
		String result = rMsg.netMSG(100, "插入数据失败");
		String info = null;
		if (object.containsKey("mainName")) {
			mainName = object.getString("mainName");
		}
		if (StringHelper.InvaildString(mainName)) {
			long currentTime = TimeHelper.nowMillis();
			if (object == null || object.size() <= 0) {
				return rMsg.netMSG(100, "插入数据失败");
			}
			try {
				if (object.containsKey("time")) {
					long time = getStamp(object.getString("time"));
					if (time < currentTime) {
						currentTime = time;
					}
				}
			} catch (Exception e) {
				nlogger.logout(e);
				currentTime = TimeHelper.nowMillis();
			}
			object.put("time", currentTime);
			object.put("state", 2);
			String value = object.get("content").toString();

			value = codec.DecodeHtmlTag(value);
			value = codec.decodebase64(value);
			System.out.println("value: " + value);
			object.escapeHtmlPut("content", value);
			// object.put("content", value);
			info = model.data(object).autoComplete().insertOnce().toString();
		}
		return (StringHelper.InvaildString(info)) ? rMsg.netMSG(0, "插入数据成功") : result;

	}

	/**
	 * 获取时间戳
	 * 
	 * @project SYJJContent
	 * @package interfaceApplication
	 * @file Content.java
	 * 
	 * @param time
	 * @return
	 *
	 */
	private long getStamp(String time) {
		long times = 0;
		int length = time.length();
		SimpleDateFormat simpleDateFormat = null;
		Date date = null;
		try {
			if (length > 0 && length <= 10) {
				simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
				date = simpleDateFormat.parse(time);
				times = date.getTime();
			} else if (length > 10 && length < 19) {
				simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm");
				date = simpleDateFormat.parse(time);
				times = date.getTime();
			} else {
				times = TimeHelper.dateToStamp(time);
			}
		} catch (Exception e) {
			nlogger.logout(e);
			times = TimeHelper.nowMillis();
		}
		return times;
	}

	/**
	 * 文章内容分编码
	 * 
	 * @project SYJJContent
	 * @package interfaceApplication
	 * @file Content.java
	 * 
	 * @param object
	 * @return
	 *
	 */
	@SuppressWarnings("unchecked")
	private JSONObject dencode(JSONObject object) {
		String temp = "";
		if (object != null && object.size() > 0) {
			if (object.containsKey("content")) {
				temp = object.getString("content");
				temp = codec.encodebase64(temp);
				temp = codec.EncodeHtmlTag(temp);
			}
			object.put("content", temp);
			if (object.containsKey("image")) {
				temp = object.getString("image");
				temp = codec.EncodeHtmlTag(temp);
				object.put("image", temp);
			}
		}
		return object;
	}

	/**
	 * 获取栏目id
	 * 
	 * @project SYJJContent
	 * @package interfaceApplication
	 * @file Content.java
	 * 
	 * @param appids
	 * @param column
	 * @return
	 *
	 */
	private String getOgid(String appids, String wbid, String column) {
		DBHelper helper = new DBHelper("mongodb", "objectGroup");
		db db = helper.bind(appids);
		if (!checkHelper.isNum(column)) {
			JSONObject object = db.eq("name", column).eq("wbid", wbid).find();
			column = (object != null && object.size() > 0) ? object.getMongoID("_id") : null;
		}
		return column;
	}

	/**
	 * 判断文章是否存在
	 * 
	 * @project SYJJContent
	 * @package interfaceApplication
	 * @file Content.java
	 * 
	 * @param appids
	 * @param ogid
	 * @param wbid
	 * @param mainName
	 * @return
	 *
	 */
	private boolean isExsist(String appids, String ogid, String wbid, String mainName, String Content) {
		DBHelper helper = new DBHelper("mongodb", "objectList");
		db db = helper.bind(appids);
		JSONObject object = db.eq("ogid", ogid).eq("wbid", wbid).eq("mainName", mainName).eq("content", Content).find();
		return object != null && object.size() > 0;
	}

	/**
	 * 插入省级抽检信息数据西信息
	 * 
	 * @project SYJJContent
	 * @package interfaceApplication
	 * @file Content.java
	 * 
	 * @param info
	 * @return
	 *
	 */
	public String SetData(String ogid) {
		JSONObject object = JSONObject.toJSON(execRequest.getChannelValue(grapeHttpUnit.formdata).toString());
		return SetData(ogid, object.getString("param"));
	}

	@SuppressWarnings("unchecked")
	private String SetData(String ogid, String info) {
		db db = bind();
		String mainName = "";
		int code = 99;
		String result = rMsg.netMSG(100, "导入数据失败");
		info = codec.DecodeFastJSON(info);
		JSONObject object = JSONObject.toJSON(info);
		if (object != null && object.size() > 0) {
			if (object.containsKey("title")) {
				mainName = object.getString("title");
			}
			if (ContentIsExsist(ogid, mainName)) {
				return rMsg.netMSG(1, "该文章已存在");
			}
			object.put("searchKey", ogid);
			object = dencode(object);
			long currentTime = TimeHelper.nowMillis();
			try {
				if (object.containsKey("publishData")) {
					System.out.println(object.getString("publishData"));
					long time = getStamp(object.getString("publishData"));
					if (time < currentTime) {
						currentTime = time;
					}
				}
			} catch (Exception e) {
				nlogger.logout(e);
				currentTime = TimeHelper.nowMillis();
			}
			object.put("publishData", currentTime);
			String value = object.get("content").toString();
			value = codec.DecodeHtmlTag(value);
			value = codec.decodebase64(value);
			object.escapeHtmlPut("content", value);
			code = db.data(object).insertOnce().toString() != null ? 0 : 99;
			result = code == 0 ? rMsg.netMSG(0, "数据存储成功") : result;
		}
		System.out.println("result: " + result);
		return result;
	}

	private boolean ContentIsExsist(String ogid, String mainName) {
		db db = bind();
		JSONObject object = db.eq("searchKey", ogid).eq("title", mainName).find();
		return object != null && object.size() > 0;
	}

	/**
	 * 任务信息
	 * 
	 * @project SYJJContent
	 * @package interfaceApplication
	 * @file Content.java
	 * 
	 * @param searchK
	 * @param pageIndex
	 * @param pageSize
	 * @param startDate
	 * @param endDate
	 * @return
	 *
	 */
	@SuppressWarnings("unchecked")
	public String getTask(String searchK, String pageIndex, String pageSize, String startDate, String endDate) throws Exception {
		long start = 0, end = 0;
		JSONArray array = null;
		JSONObject object = new JSONObject(), temp;
		int idx = Integer.parseInt(pageIndex);
		int Size = Integer.parseInt(pageSize);
		if (idx > 0 && Size > 0) {
			if (startDate.contains("-")) {
				if (startDate.length() > 10) {
					start = TimeHelper.dateToStamp(startDate);
				} else {
					start = DataConvert(startDate);
				}
			} else {
				start = Long.parseLong(startDate);
			}
			if (endDate.contains("-")) {
				if (endDate.length() > 10) {
					end = TimeHelper.dateToStamp(endDate);
				} else {
					end = DataConvert(endDate);
				}
			} else {
				end = Long.parseLong(endDate);
			}
			long total = 0;
			String oid = "";
			try {
				db db = bind();
				db.eq("searchKey", searchK);
				db.gt("publishData", start);
				db.lt("publishData", end);
				array = db.dirty().field("_id,siteName,title,publishData,code").page(idx, Size);
				total = db.count();
				if (array != null && array.size() > 0) {
					int l = array.size();
					for (int i = 0; i < l; i++) {
						temp = (JSONObject) array.get(i);
						oid = temp.getMongoID("_id");
						temp.put("count", total);
						temp.put("code", "/" + appsProxy.appidString() + "/SYJJContent/Content/getContentInfo/" + oid + "/int:" + pageIndex);
						array.set(i, temp);
					}
				}
			} catch (Exception e) {
				nlogger.logout(e);
				array = null;
			}
		}
		object.put("content", (array != null && array.size() > 0) ? array : new JSONArray());
		return object.toString();
	}

	/**
	 * 时间转时间戳
	 * 
	 * @project GrapeInfoCollection
	 * @package interfaceApplication
	 * @file CollectInfo.java
	 * 
	 * @param value
	 * @param dataType
	 * @return
	 *
	 */
	private long DataConvert(String value) {
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
		long ts = 0;
		try {
			Date date = simpleDateFormat.parse(value);
			ts = date.getTime();
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return ts;
	}

	/**
	 * 获取内容信息
	 * 
	 * @project SYJJContent
	 * @package interfaceApplication
	 * @file Content.java
	 * 
	 * @param _id
	 *            文章唯一标识符
	 * @param idx
	 *            当前页
	 * @return
	 *
	 */
	public String getContentInfo(String _id, int idx) {
		String title = "";
		String siteName = "";
		String author = "";
		String publishData = "";
		String contents = "";
		JSONObject object = bind().eq("_id", _id).find();
		if (object != null && object.size() > 0) {
			if (object != null && object.size() > 0) {
				if (object.containsKey("siteName")) {
					siteName = object.getString("siteName");
				}
				if (object.containsKey("title")) {
					title = object.getString("title");
				}
				if (object.containsKey("author")) {
					author = object.getString("author");
				}
				if (object.containsKey("publishData")) {
					publishData = object.getString("publishData");
					long time = Long.parseLong(publishData);
					publishData = TimeHelper.stampToDate(time);
				}
				if (object.containsKey("content")) {
					contents = (String) object.escapeHtmlGet("content");
					contents = attamUrl(contents);
				}
			}
		}
		return getHtml(siteName, title, author, publishData, contents, idx);
	}

	private String attamUrl(String contents) {
		String temp, newurl, tempdir;
		Pattern ATTR_PATTERN = Pattern.compile(".*href=\"(.*)\"", Pattern.CASE_INSENSITIVE);
		String dir = "http://syj.tl.gov.cn/2205/2212/shijcjxx/";
		Matcher matcher = ATTR_PATTERN.matcher(contents);
		List<String> list = new ArrayList<String>();
		while (matcher.find()) {
			list.add(matcher.group(1));
		}
		if (list != null && list.size() > 0) {
			int l = list.size();
			for (int i = 0; i < l; i++) {
				temp = list.get(i);
				tempdir = dir + temp.substring(4, 10);
				if (!temp.contains("http://")) {
					newurl = tempdir + StringHelper.fixLeft(temp, ".");
					contents = contents.replace(temp, newurl);
				}
			}
		}
		return contents;
	}

	private String getHtml(String siteName, String title, String author, String publishData, String content, int idx) {
		String commonHtml = "<title>" + title + "</title>";
		commonHtml += "<meta name=\"subtitle\" content=" + "\"" + siteName + "\"" + "/>";
		commonHtml += "<meta name=\"author\" content=" + "\"" + author + "\"" + "/>";
		commonHtml += "<meta name=\"pubdate\" content=" + "\"" + publishData + "\"" + "/>";
		commonHtml += "<meta name=\"ContentStart\"/>" + content;
		commonHtml += "<meta name=\"ContentEnd\"/>";
		commonHtml += "<meta name=\"pageSize\" content=" + "\"" + idx + "\"" + "/>";
		return commonHtml;
	}
}
