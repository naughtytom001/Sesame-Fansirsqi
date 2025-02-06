package naughtytom.xposed.sesame.task.antForest;
import static naughtytom.xposed.sesame.task.antForest.AntForest.ecoLifeOpen;
import static naughtytom.xposed.sesame.task.antForest.AntForest.ecoLifeOption;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import naughtytom.xposed.sesame.data.Config;
import naughtytom.xposed.sesame.util.JsonUtil;
import naughtytom.xposed.sesame.util.Log;
import naughtytom.xposed.sesame.util.Maps.UserMap;
import naughtytom.xposed.sesame.util.ResUtil;
import naughtytom.xposed.sesame.util.StringUtil;
import naughtytom.xposed.sesame.util.ThreadUtil;
import naughtytom.xposed.sesame.model.modelFieldExt.TextModelField;
import naughtytom.xposed.sesame.util.ToastUtil;
public class EcoLife {
    public static final String TAG = EcoLife.class.getSimpleName();
    public static TextModelField photoGuangPanBefore = new TextModelField("photoGuangPanBefore", "绿色|光盘前图片ID", "");
    public static TextModelField photoGuangPanAfter = new TextModelField("photoGuangPanAfter", "绿色|光盘后图片ID", "");
    public static void resetPhotoGuangPan() {
        try {
            if (photoGuangPanBefore != null) {
                photoGuangPanBefore.reset();
            } else {
                throw new NullPointerException("photoGuangPanBefore is null");
            }
            if (photoGuangPanAfter != null) {
                photoGuangPanAfter.reset();
            } else {
                throw new NullPointerException("photoGuangPanAfter is null");
            }
        } catch (NullPointerException e) {
            Log.printStackTrace(TAG+" Error resetting photoGuangPan", e);
            ToastUtil.showToast(e.getMessage());
        }
    }
    /**
     * 执行绿色行动任务，包括查询任务开通状态、开通绿色任务、执行打卡任务等操作。
     * 1. 调用接口查询绿色行动的首页数据，检查是否成功。
     * 2. 如果绿色任务尚未开通，且用户未开通绿色任务，则记录日志并返回。
     * 3. 如果绿色任务尚未开通，且用户已开通绿色任务，则尝试开通绿色任务。
     * 4. 开通绿色任务成功后，再次查询任务状态，并更新数据。
     * 5. 获取任务的日期标识和任务列表，执行打卡任务。
     * 6. 如果绿色打卡设置为启用，执行 `ecoLifeTick` 方法提交打卡任务。
     * 7. 如果光盘打卡设置为启用，执行 `photoGuangPan` 方法上传光盘照片。
     * 8. 异常发生时，记录错误信息并打印堆栈。
     */
    public static void ecoLife() {
        try {
            // 查询首页信息
            JSONObject jsonObject = new JSONObject(AntForestRpcCall.ecolifeQueryHomePage());
            if (!jsonObject.optBoolean("success")) {
                Log.runtime(TAG + ".ecoLife.queryHomePage", jsonObject.optString("resultDesc"));
                return;
            }
            JSONObject data = jsonObject.getJSONObject("data");
            if (!ecoLifeOpen.getValue()) {
                return;
            }
            if (!data.getBoolean("openStatus")) {
                if (!openEcoLife()) {
                    return;
                }
                jsonObject = new JSONObject(AntForestRpcCall.ecolifeQueryHomePage());
                data = jsonObject.getJSONObject("data");
            }
            // 获取当天的积分和任务列表
            String dayPoint = data.getString("dayPoint");
            JSONArray actionListVO = data.getJSONArray("actionListVO");
            // 绿色打卡
            if (ecoLifeOption.getValue().contains("tick")) {
                ecoLifeTick(actionListVO, dayPoint);
            }
            if (ecoLifeOption.getValue().contains("plate")) {
                photoGuangPan(dayPoint);
            }
        } catch (Throwable th) {
            Log.runtime(TAG, "ecoLife err:");
            Log.printStackTrace(TAG, th);
        }
    }
    /**
     * 封装绿色任务开通的逻辑
     *
     * @return 是否成功开通绿色任务
     */
    public static boolean openEcoLife() throws JSONException {
        ThreadUtil.sleep(300);
        JSONObject jsonObject = new JSONObject(AntForestRpcCall.ecolifeOpenEcolife());
        if (!jsonObject.optBoolean("success")) {
            Log.runtime(TAG + ".ecoLife.openEcolife", jsonObject.optString("resultDesc"));
            return false;
        }
        String opResult = JsonUtil.getValueByPath(jsonObject, "data.opResult");
        if (!"true".equals(opResult)) {
            return false;
        }
        Log.forest("绿色任务🍀报告大人，开通成功(～￣▽￣)～可以愉快的玩耍了");
        ThreadUtil.sleep(300);
        return true;
    }
    /**
     * 执行绿色行动打卡任务，遍历任务列表，依次提交每个未完成的任务。
     * 1. 遍历给定的任务列表（`actionListVO`），每个任务项包含多个子任务。
     * 2. 对于每个子任务，检查其是否已完成，如果未完成则提交打卡请求。
     * 3. 特别处理任务 ID 为 "photoguangpan" 的任务，跳过该任务的打卡。
     * 4. 如果任务打卡成功，记录成功日志；否则记录失败原因。
     * 5. 每次打卡请求后，等待 500 毫秒以避免请求过于频繁。
     * 6. 异常发生时，记录详细的错误信息。
     *
     * @param actionListVO 任务列表，每个任务包含多个子任务
     * @param dayPoint     任务的日期标识，用于标识任务的日期
     */
    public static void ecoLifeTick(JSONArray actionListVO, String dayPoint) {
        try {
            String source = "source";
            for (int i = 0; i < actionListVO.length(); i++) {
                JSONObject actionVO = actionListVO.getJSONObject(i);
                JSONArray actionItemList = actionVO.getJSONArray("actionItemList");
                for (int j = 0; j < actionItemList.length(); j++) {
                    JSONObject actionItem = actionItemList.getJSONObject(j);
                    if (!actionItem.has("actionId")) continue;
                    if (actionItem.getBoolean("actionStatus")) continue;
                    String actionId = actionItem.getString("actionId");
                    String actionName = actionItem.getString("actionName");
                    if ("photoguangpan".equals(actionId)) continue;
                    ThreadUtil.sleep(300);
                    JSONObject jo = new JSONObject(AntForestRpcCall.ecolifeTick(actionId, dayPoint, source));
                    if (ResUtil.checkResCode(jo)) {
                        Log.forest("绿色打卡🍀[" + actionName + "]"); // 成功打卡日志
                    } else {
                        // 记录失败原因
                        Log.error(TAG + jo.getString("resultDesc"));
                        Log.error(TAG + jo);
                    }
                    ThreadUtil.sleep(300);
                }
            }
        } catch (Throwable th) {
            Log.runtime(TAG, "ecoLifeTick err:");
            Log.printStackTrace(TAG, th);
        }
    }
    /**
     * 执行光盘行动任务，上传餐前餐后照片并提交任务。
     * 1. 查询当前任务的状态。
     * 2. 如果任务未完成，检查是否已有餐前餐后照片的URL，如果没有则从接口获取并保存。
     * 3. 上传餐前餐后照片，上传成功后提交任务，标记任务为完成。
     * 4. 如果任务已完成，则不做任何操作。
     * 5. 如果遇到任何错误，记录错误信息并停止执行。
     *
     * @param dayPoint 任务的日期标识，用于标识任务的日期
     */
    public static void photoGuangPan(String dayPoint) {
        try {
            String source = "renwuGD"; // 任务来源标识
            // 查询今日任务状态
            String str = AntForestRpcCall.ecolifeQueryDish(source, dayPoint);
            JSONObject jo = new JSONObject(str);
            // 如果请求失败，则记录错误信息并返回
            if (!ResUtil.checkSuccess(jo)) {
                Log.runtime(TAG + ".photoGuangPan.ecolifeQueryDish", jo.optString("resultDesc"));
                return;
            }
            boolean isDone = false; // 任务是否完成的标志
            // 获取餐前餐后照片的URL
            String photoGuangPanBeforeStr = photoGuangPanBefore.getValue();
            String photoGuangPanAfterStr = photoGuangPanAfter.getValue();
            if (photoGuangPanBeforeStr == null || photoGuangPanAfterStr == null || Objects.equals(photoGuangPanBeforeStr, photoGuangPanAfterStr) || StringUtil.isEmpty(photoGuangPanBeforeStr) || StringUtil.isEmpty(photoGuangPanAfterStr)) {
                // 如果没有照片URL或两者相同，需重新获取照片URL
                JSONObject data = jo.optJSONObject("data");
                if (data != null) {
                    String beforeMealsImageUrl = data.optString("beforeMealsImageUrl");
                    String afterMealsImageUrl = data.optString("afterMealsImageUrl");
                    // 如果餐前和餐后照片URL都存在，进行提取
                    if (!StringUtil.isEmpty(beforeMealsImageUrl) && !StringUtil.isEmpty(afterMealsImageUrl)) {
                        // 使用正则从URL中提取照片的路径部分
                        Pattern pattern = Pattern.compile("img/(.*)/original");
                        Matcher beforeMatcher = pattern.matcher(beforeMealsImageUrl);
                        if (beforeMatcher.find()) {
                            photoGuangPanBeforeStr = beforeMatcher.group(1);
                            photoGuangPanBefore.setValue(photoGuangPanBeforeStr); // 保存餐前照片路径
                        }
                        Matcher afterMatcher = pattern.matcher(afterMealsImageUrl);
                        if (afterMatcher.find()) {
                            photoGuangPanAfterStr = afterMatcher.group(1);
                            photoGuangPanAfter.setValue(photoGuangPanAfterStr); // 保存餐后照片路径
                        }
                        // 保存配置
                        Config.save(UserMap.getCurrentUid(), false);
                        isDone = true;
                    }
                }
            } else {
                isDone = true;
            }
            if ("SUCCESS".equals(JsonUtil.getValueByPath(jo, "data.status"))) {
                return;
            }
            if (!isDone) {
                Log.forest("光盘行动🍽️请先完成一次光盘打卡");
                return;
            }
            ThreadUtil.sleep(300);
            str = AntForestRpcCall.ecolifeUploadDishImage("BEFORE_MEALS", photoGuangPanBeforeStr, 0.16571736, 0.07448776, 0.7597949, dayPoint);
            jo = new JSONObject(str);
            if (!jo.optBoolean("success")) {
                Log.runtime(TAG + "上传餐前图片", jo.optString("resultDesc"));
                return;
            }
            ThreadUtil.sleep(300);
            str = AntForestRpcCall.ecolifeUploadDishImage("AFTER_MEALS", photoGuangPanAfterStr, 0.00040030346, 0.99891376, 0.0006858421, dayPoint);
            jo = new JSONObject(str);
            if (!jo.optBoolean("success")) {
                Log.runtime(TAG + "上传餐后图片", jo.optString("resultDesc"));
                return;
            }
            // 提交任务
            str = AntForestRpcCall.ecolifeTick("photoguangpan", dayPoint, source);
            jo = new JSONObject(str);
            // 如果提交失败，记录错误信息并返回
            if (!jo.optBoolean("success")) {
                Log.runtime(TAG + "光盘打卡", jo.optString("resultDesc"));
                return;
            }
            // 任务完成，输出完成日志
            Log.forest("光盘行动🍽️任务完成");
        } catch (Throwable t) {
            // 捕获异常，记录错误信息和堆栈追踪
            Log.runtime(TAG, "photoGuangPan err:");
            Log.printStackTrace(TAG, t);
        }
    }
}
