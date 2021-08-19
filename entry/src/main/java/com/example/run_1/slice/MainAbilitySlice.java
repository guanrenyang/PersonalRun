package com.example.run_1.slice;

import com.example.run_1.ResourceTable;
import ohos.aafwk.ability.AbilitySlice;
import ohos.aafwk.content.Intent;
import ohos.agp.components.Button;
import ohos.agp.components.Component;
import ohos.agp.components.Text;
import ohos.agp.components.TickTimer;
import ohos.agp.utils.Color;
import ohos.agp.window.service.Window;
import ohos.app.Context;
import ohos.app.dispatcher.TaskDispatcher;
import ohos.app.dispatcher.task.Revocable;
import ohos.app.dispatcher.task.TaskPriority;
import ohos.hiviewdfx.HiLog;
import ohos.utils.system.SystemCapability;
import ohos.vibrator.agent.VibratorAgent;
import ohos.vibrator.bean.VibrationPattern;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;

class RunUnit {
    protected long  run_time;
    protected String run_type;//{"慢速跑", "轻松跑", "间歇跑", "快走"}

    public RunUnit(long time, String type) {
        run_time = time;
        run_type = type;
    }
}

class RunUnitList{
    /*
    保存一次训练的所有训练单元的线性表
     */
    LinkedList<RunUnit> run_unit_list = new LinkedList<RunUnit>();

    public void addUnit(long time, String type){
        RunUnit temp = new RunUnit(time, type);
        run_unit_list.addLast(temp);
    }
    public int size(){
        return run_unit_list.size();
    }
    public long getTime(int i){
        return run_unit_list.get(i).run_time;
    }
    public String getType(int i){
        return run_unit_list.get(i).run_type;
    }

}

public class MainAbilitySlice extends AbilitySlice  {

    RunUnitList run_unit_list;

    TickTimer tick_timer;
    Text run_type_text;
    Text time_text;
    Button run_start;
    Button run_pause;

    VibratorAgent vibratorAgent; // 振动控制器
    List<Integer> vibratorList;  // 振动器列表

    static long MINUTE = 60 * 1000;
    static long SECOND = 1000;

    int vibratorTiming = 1 * (int) SECOND;
    static String TIME_FORMAT = "HH:mm:ss";
    @Override
    public void onStart(Intent intent) {

        super.onStart(intent);
        super.setUIContent(ResourceTable.Layout_ability_main);

        Window thisWindow = getWindow();
        thisWindow.setStatusBarColor(Color.getIntColor("#A6C2BE"));

        // 定义保存一次训练计划的数据结构RunUnitList
        run_unit_list = new RunUnitList();

        for (long i = 30*MINUTE;i>0;i-=3*MINUTE){
            run_unit_list.addUnit(2*MINUTE, "慢跑");
            run_unit_list.addUnit(1*MINUTE, "快走");
        }

        // 1. 找到组件对象
        tick_timer = (TickTimer) findComponentById(ResourceTable.Id_Tick_Timer);
        time_text = (Text) findComponentById(ResourceTable.Id_timer);
        run_type_text = (Text) findComponentById(ResourceTable.Id_Run_Type);
        run_start = (Button) findComponentById(ResourceTable.Id_Start);
        run_pause = (Button) findComponentById(ResourceTable.Id_Pause);

        // 定时器基本设置
        tick_timer.setCountDown(false);
        tick_timer.setFormat(TIME_FORMAT);
        tick_timer.setVisibility(Component.HIDE);

        // 2. 给组件绑定事件
        tick_timer.setTickListener(this::onTickTimerUpdate);
        run_start.setClickedListener(this::onClick);
        run_pause.setClickedListener(this::onClick);

        //振动器
        vibratorAgent = new VibratorAgent();
        vibratorList = vibratorAgent.getVibratorIdList();
        if (!vibratorList.isEmpty()) {
            int vibrator_id = vibratorList.get(0);
        }

    }

    boolean first_clicked = true;   // 开始按钮是否第一次被点击
    long start_time;                // 记录每一个RunUnit的开始时间
    long unit_paused_interval;      // paused_interval = 暂停按下的时间 - start_time, 这个变量针对
    long nearest_pause_time;
                                    // 暂停按下以后更新 start_time = current_time - paused_interval
    long all_paused_time = 0;   // process_paused_interval = 暂停的总时间
    long tickTimer_bias;            // 计时器时间与实际应显示的时间之间的偏差
    int current_unit=0;

    boolean is_paused = false;
    // 是否正在暂停中
    //点击开始键值则启动计时器
    private void onClick(Component component) {
        if (component == run_start && first_clicked) {

            first_clicked = false;
            tick_timer.start();

            start_time = String2Long(tick_timer.getText());        // 此时start_time为`程序启动`到`点击开始`所经过的时间
            tickTimer_bias = String2Long(tick_timer.getText());    // 初始化 tickTimer_bias

            run_type_text.setText(run_unit_list.getType(current_unit));
        }
        else if (component == run_pause && !first_clicked){
            is_paused = !is_paused;
            unit_paused_interval = String2Long(tick_timer.getText()) - start_time;                // 获取暂停键按下的时间
            if (is_paused){
                run_type_text.setText("暂停中");
                nearest_pause_time = String2Long(tick_timer.getText());
            }
            else {
                all_paused_time += String2Long(tick_timer.getText()) - nearest_pause_time;
                run_type_text.setText(run_unit_list.getType(current_unit)) ;
            }
        }

    }

    private void onTickTimerUpdate(TickTimer tickTimer) {
        long current_time = String2Long(tickTimer.getText());


        if (is_paused){// 如果已暂停, start_time 和 current_time 保持原来的距离并同步更新
            start_time = current_time - unit_paused_interval;
        }
        else {
            time_text.setText(Long2String(current_time-tickTimer_bias - all_paused_time));
        }
        if(current_time - start_time >= run_unit_list.getTime(current_unit)){ // 减去振动时常

            TaskDispatcher globalTaskDispatcher = getGlobalTaskDispatcher(TaskPriority.DEFAULT);
             globalTaskDispatcher.syncDispatch(new Runnable() {
                @Override
                public void run() {
                    // 更新跑步姿势或者结束的时候都需要 振动
                    if (!vibratorList.isEmpty()) {
                        // 获取振动器id
                        int vibratorId = vibratorList.get(0);

                        // 创建指定时常的一次性振动
                        boolean vibrateResult = vibratorAgent.startOnce(vibratorId, vibratorTiming);
                    }
                }
            });


            current_unit++;
            start_time = String2Long(tickTimer.getText());

            if (current_unit == run_unit_list.size()){
                tickTimer.stop();
                run_type_text.setText("结束训练");
            }
            else {
                run_type_text.setText(run_unit_list.getType(current_unit));
            }
        }
    }

    //把字符串类型的时间，变成毫秒值（long）
    public long String2Long(String time){
        SimpleDateFormat sdf = new SimpleDateFormat(TIME_FORMAT);
        Date date = null;
        try {
            date = sdf.parse(time);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        long result = date.getTime();
        return result;
    }
    public String Long2String(long time) {  // time 单位ms
        SimpleDateFormat sdf = new SimpleDateFormat(TIME_FORMAT);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT+:08:00"));
        Date date = new Date();
        date.setTime(time);

        return sdf.format(date);
    }
    @Override
    public void onActive() {
        super.onActive();
    }

    @Override
    public void onForeground(Intent intent) {
        super.onForeground(intent);
    }
}
