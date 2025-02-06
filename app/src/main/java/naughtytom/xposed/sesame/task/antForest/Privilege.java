package naughtytom.xposed.sesame.task.antForest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import naughtytom.xposed.sesame.util.Log;
import naughtytom.xposed.sesame.util.StatusUtil;
public class Privilege {
    public static final String TAG = Privilege.class.getSimpleName();
    //青春特权🌸领取
static boolean youthPrivilege() {
    try {
        if (!StatusUtil.canYouthPrivilegeToday()) return false;
        List<List<String>> taskList = Arrays.asList(
                Arrays.asList("DNHZ_SL_college", "DAXUESHENG_SJK", "双击卡"),
                Arrays.asList("DXS_BHZ", "NENGLIANGZHAO_20230807", "保护罩"),
                Arrays.asList("DXS_JSQ", "JIASUQI_20230808", "加速器")
        );
        List<String> resultList = new ArrayList<>();
        for (List<String> task : taskList) {
            String queryParam = task.get(0); // 用于 queryTaskListV2 方法的第一个参数
            String receiveParam = task.get(1); // 用于 receiveTaskAwardV2 方法的第二个参数
            String taskName = task.get(2); // 标记名称
            String queryResult = AntForestRpcCall.queryTaskListV2(queryParam);
            JSONObject getTaskStatusObject = new JSONObject(queryResult);
            JSONArray taskInfoList = getTaskStatusObject.getJSONArray("forestTasksNew")
                    .getJSONObject(0).getJSONArray("taskInfoList");
            resultList.addAll(handleTaskList(taskInfoList, receiveParam, taskName));
        }
        boolean allSuccessful = true;
        for (String result : resultList) {
            if (!"处理成功".equals(result)) {
                allSuccessful = false;
                break;
            }
        }
        if (allSuccessful) {
            StatusUtil.setYouthPrivilegeToday();
            return true;
        } else {
            return false;
        }
    } catch (Exception e) {
        Log.runtime(AntForest.TAG, "youthPrivilege err:");
        Log.printStackTrace(AntForest.TAG, e);
        return false;
    }
}
    /**
     * 处理任务列表
     */
    private static List<String> handleTaskList(JSONArray taskInfoList, String receiveParam, String taskName) throws JSONException {
        List<String> resultList = new ArrayList<>();
        for (int i = 0; i < taskInfoList.length(); i++) {
            JSONObject taskInfo = taskInfoList.getJSONObject(i);
            JSONObject taskBaseInfo = taskInfo.getJSONObject("taskBaseInfo");
            if (receiveParam.equals(taskBaseInfo.getString("taskType"))) {
                String taskStatus = taskBaseInfo.getString("taskStatus");
                if ("RECEIVED".equals(taskStatus)) {
                    Log.forest("青春特权🌸[" + taskName + "]已领取");
                } else if ("FINISHED".equals(taskStatus)) {
                    String receiveResult = AntForestRpcCall.receiveTaskAwardV2(receiveParam);
                    JSONObject resultOfReceive = new JSONObject(receiveResult);
                    String resultDesc = resultOfReceive.getString("desc");
                    resultList.add(resultDesc);
                    if (resultDesc.equals("处理成功")) {
                        Log.forest("青春特权🌸[" + taskName + "]领取成功");
                    } else {
                        Log.forest("青春特权🌸[" + taskName + "]领取结果：" + resultDesc);
                    }
                }
            }
        }
        return resultList;
    }
    /**
 * 青春特权每日签到红包
 */
static void studentSignInRedEnvelope() {
    try {
        Calendar calendar = Calendar.getInstance();
        int currentHour = calendar.get(Calendar.HOUR_OF_DAY);
        final int START_HOUR = 5; // 5:00 AM
        final int END_HOUR = 10;  // 10:00 AM
        if (currentHour < START_HOUR) {
            Log.forest("青春特权🧧5点前不执行签到");
            return;
        }
        if (StatusUtil.canStudentTask()) {
            String tag = currentHour < END_HOUR ? "double" : "single";
            studentTaskHandle(tag);
        } else {
            Log.record("青春特权🧧今日已完成签到");
        }
    } catch (Exception e) {
        Log.runtime(TAG, "studentSignInRedEnvelope错误:");
        Log.printStackTrace(TAG, e);
        Log.record("青春特权🧧执行异常：" + e.getMessage());
    }
}
    /**
     * 学生签到执行逻辑
     */
    static void studentTask(String tag) {
        try {
            String result = AntForestRpcCall.studentCheckin();
            if (result == null || result.isEmpty()) {
                Log.record("青春特权🧧签到失败：返回数据为空");
                return;
            }
            JSONObject resultJson = new JSONObject(result);
            // 检查返回码
            String resultCode = resultJson.optString("resultCode", "");
            if (!"SUCCESS".equals(resultCode)) {
                String resultDesc = resultJson.optString("resultDesc", "未知错误");
                if (resultDesc.contains("不匹配")) {
                    Log.forest("青春特权🧧" + tag + "：" + resultDesc + "可能账户不符合条件");
                } else {
                    Log.forest("青春特权🧧" + tag + "：" + resultDesc);
                }
                return;
            }
            String resultDesc = resultJson.optString("resultDesc", "签到成功");
            Log.forest("青春特权🧧" + tag + "：" + resultDesc);
            StatusUtil.setStudentTaskToday();
        } catch (JSONException e) {
            Log.runtime(TAG, "studentTask JSON解析错误:");
            Log.printStackTrace(TAG, e);
            Log.record("青春特权🧧签到异常：" + e.getMessage());
        } catch (Exception e) {
            Log.runtime(TAG, "studentTask其他错误:");
            Log.printStackTrace(TAG, e);
            Log.record("青春特权🧧签到异常：" + e.getMessage());
        }
    }
    /**
     * 处理不在签到时间范围内的逻辑
     */
    private static void studentTaskHandle(String tag) {
        try {
            if (!StatusUtil.canStudentTask()) {
                Log.record("青春特权🧧今日已达上限");
                return;
            }
            String response = AntForestRpcCall.studentQqueryCheckInModel();
            if (response == null || response.isEmpty()) {
                Log.record("青春特权🧧查询失败：返回数据为空");
                return;
            }
            JSONObject responseJson = new JSONObject(response);
            // 检查返回码
            if (responseJson.has("resultCode")) {
                String resultCode = responseJson.getString("resultCode");
                if (!"SUCCESS".equals(resultCode)) {
                    String resultDesc = responseJson.optString("resultDesc", "未知错误");
                    Log.record("青春特权🧧查询失败：" + resultDesc);
                    return;
                }
            }
            // 安全获取 studentCheckInInfo
            JSONObject studentCheckInInfo = responseJson.optJSONObject("studentCheckInInfo");
            if (studentCheckInInfo == null) {
                Log.record("青春特权🧧查询失败：无签到信息");
                return;
            }
            // 安全获取 action
            String action = studentCheckInInfo.optString("action", "");
            if (action.isEmpty()) {
                Log.record("青春特权🧧查询失败：无操作信息");
                return;
            }
            if ("DO_TASK".equals(action)) {
                Log.record("青春特权🧧今日已签到");
                StatusUtil.setStudentTaskToday();
            } else {
                studentTask(tag);
            }
        } catch (JSONException e) {
            Log.runtime(TAG, "studentTaskHandle JSON解析错误:");
            Log.printStackTrace(TAG, e);
            Log.record("青春特权🧧签到异常：" + e.getMessage());
        } catch (Exception e) {
            Log.runtime(TAG, "studentTaskHandle其他错误:");
            Log.printStackTrace(TAG, e);
            Log.record("青春特权🧧签到异常：" + e.getMessage());
        }
    }
}
