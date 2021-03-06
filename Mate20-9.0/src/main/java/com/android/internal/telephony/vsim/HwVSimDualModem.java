package com.android.internal.telephony.vsim;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Message;
import android.os.SystemProperties;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.HwVSimPhoneFactory;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.vsim.HwVSimController;
import com.android.internal.telephony.vsim.HwVSimEventReport;
import com.android.internal.telephony.vsim.HwVSimModemAdapter;
import com.android.internal.telephony.vsim.HwVSimSlotSwitchController;
import com.android.internal.telephony.vsim.process.HwVSimProcessor;
import java.util.Arrays;

public class HwVSimDualModem extends HwVSimModemAdapter {
    private static final String LOG_TAG = "HwVSimDualModem";
    private static final Object mLock = new Object();
    private static HwVSimDualModem sModem;

    public static HwVSimDualModem create(HwVSimController vsimController, Context context, CommandsInterface vsimCi, CommandsInterface[] cis) {
        HwVSimDualModem hwVSimDualModem;
        synchronized (mLock) {
            if (sModem == null) {
                sModem = new HwVSimDualModem(vsimController, context, vsimCi, cis);
                hwVSimDualModem = sModem;
            } else {
                throw new RuntimeException("VSimController already created");
            }
        }
        return hwVSimDualModem;
    }

    public static HwVSimDualModem getInstance() {
        HwVSimDualModem hwVSimDualModem;
        synchronized (mLock) {
            if (sModem != null) {
                hwVSimDualModem = sModem;
            } else {
                throw new RuntimeException("VSimController not yet created");
            }
        }
        return hwVSimDualModem;
    }

    private HwVSimDualModem(HwVSimController vsimController, Context context, CommandsInterface vsimCi, CommandsInterface[] cis) {
        super(vsimController, context, vsimCi, cis);
    }

    public void onGetSimSlotDone(HwVSimProcessor processor, AsyncResult ar) {
        if (ar == null || ar.userObj == null) {
            loge("onGetSimSlotDone, param is null !");
            return;
        }
        HwVSimRequest request = (HwVSimRequest) ar.userObj;
        int subId = request.mSubId;
        int mainSlot = 0;
        boolean isVSimOnM0 = false;
        logd("onGetSimSlotDone : subId = " + subId);
        if (ar.exception == null && ar.result != null && ((int[]) ar.result).length == 2) {
            int[] slots = (int[]) ar.result;
            int[] responseSlots = new int[3];
            CommandsInterface ci = getCiBySub(2);
            logd("onGetSimSlotDone : result = " + Arrays.toString(slots));
            if (ci != null) {
                isVSimOnM0 = HwVSimUtilsInner.isRadioAvailable(2);
            }
            responseSlots[0] = slots[0];
            responseSlots[1] = slots[1];
            responseSlots[2] = 2;
            if (slots[0] == 0 && slots[1] == 1 && !isVSimOnM0) {
                mainSlot = 0;
            } else if (slots[0] == 1 && slots[1] == 0 && !isVSimOnM0) {
                mainSlot = 1;
            } else if (slots[0] == 2 && slots[1] == 1 && isVSimOnM0) {
                mainSlot = 0;
                responseSlots[2] = 0;
            } else if (slots[0] == 2 && slots[1] == 0 && isVSimOnM0) {
                mainSlot = 1;
                responseSlots[2] = 1;
            } else {
                loge("[2Cards]getSimSlot fail , setMainSlot = " + 0);
            }
            setSimSlotTable(responseSlots);
            HwVSimPhoneFactory.setPropPersistRadioSimSlotCfg(slots);
            logd("onGetSimSlotDone : mainSlot = " + mainSlot);
            logd("onGetSimSlotDone : isVSimOnM0 = " + isVSimOnM0);
            request.setMainSlot(mainSlot);
            request.setIsVSimOnM0(isVSimOnM0);
            request.setGotSimSlotMark(true);
            if (!isVSimOnM0) {
                HwVSimPhoneFactory.savePendingDeviceInfoToSP();
            }
        } else {
            processor.doProcessException(ar, request);
        }
    }

    public HwVSimModemAdapter.SimStateInfo onGetSimStateDone(HwVSimProcessor processor, AsyncResult ar) {
        int subId = ((HwVSimRequest) ar.userObj).mSubId;
        int simIndex = 1;
        if (subId == 2) {
            simIndex = 11;
        }
        logd("onGetSimStateDone : subId = " + subId + ", simIndex = " + simIndex);
        int simEnable = 0;
        int simSub = 0;
        int simNetinfo = 0;
        if (ar.exception == null && ar.result != null && ((int[]) ar.result).length > 3) {
            simIndex = ((int[]) ar.result)[0];
            simEnable = ((int[]) ar.result)[1];
            simSub = ((int[]) ar.result)[2];
            simNetinfo = ((int[]) ar.result)[3];
            logd("onGetSimStateDone : simIndex= " + simIndex + ", simEnable= " + simEnable + ", simSub= " + simSub + ", simNetinfo= " + simNetinfo);
        }
        return new HwVSimModemAdapter.SimStateInfo(simIndex, simEnable, simSub, simNetinfo);
    }

    public void getAllCardTypes(HwVSimProcessor processor, HwVSimRequest request) {
        for (int subId = 0; subId < PHONE_COUNT; subId++) {
            if (getCiBySub(subId) == null || !HwVSimUtilsInner.isRadioAvailable(subId)) {
                request.setGotCardType(subId, true);
                int cardTypeBackup = HwVSimPhoneFactory.getUnReservedSubCardType();
                if (cardTypeBackup != -1) {
                    logd("getAllCardTypes: use backup cardtype " + cardTypeBackup + " instead of 0(NO_SIM)");
                    request.setCardType(subId, cardTypeBackup);
                } else {
                    request.setCardType(subId, 0);
                }
            } else {
                request.setGotCardType(subId, false);
                request.setCardType(subId, 0);
                getCardTypes(processor, request.clone(), subId);
            }
        }
    }

    public void onQueryCardTypeDone(HwVSimProcessor processor, AsyncResult ar) {
        if (processor == null || ar == null || ar.userObj == null) {
            loge("onQueryCardTypeDone, param is null !");
            return;
        }
        HwVSimRequest request = (HwVSimRequest) ar.userObj;
        int subId = request.mSubId;
        request.setCardType(subId, ((int[]) ar.result)[0] & 15);
        request.setGotCardType(subId, true);
        logd("onQueryCardTypeDone : subId = " + subId);
        if (request.isGotAllCardTypes()) {
            request.logCardTypes();
            this.mVSimController.updateCardTypes(request.getCardTypes());
        }
    }

    public void checkEnableSimCondition(HwVSimProcessor processor, HwVSimRequest request) {
        HwVSimModemAdapter.ExpectPara expectPara;
        HwVSimRequest hwVSimRequest = request;
        if (processor == null || hwVSimRequest == null) {
            loge("checkEnableSimCondition, param is null !");
            return;
        }
        int[] cardTypes = request.getCardTypes();
        if (cardTypes == null) {
            loge("checkEnableSimCondition, cardTypes is null !");
        } else if (cardTypes.length == 0) {
            loge("checkEnableSimCondition, cardCount == 0 !");
        } else {
            int insertedCardCount = HwVSimUtilsInner.getInsertedCardCount(cardTypes);
            logd("Enable: inserted card count = " + insertedCardCount);
            HwVSimSlotSwitchController.CommrilMode currentCommrilMode = getCommrilMode();
            logd("Enable: currentCommrilMode = " + currentCommrilMode);
            int mainSlot = request.getMainSlot();
            logd("Enable: mainSlot = " + mainSlot);
            int savedMainSlot = getVSimSavedMainSlot();
            logd("Enable: savedMainSlot = " + savedMainSlot);
            if (savedMainSlot == -1) {
                setVSimSavedMainSlot(mainSlot);
            }
            if (insertedCardCount == 0) {
                expectPara = getExpectParaCheckEnableNoSim(hwVSimRequest);
            } else if (insertedCardCount == 1) {
                expectPara = getExpectParaCheckEnableOneSim(hwVSimRequest);
            } else {
                int reservedSub = this.mVSimController.getUserReservedSubId();
                if (reservedSub == -1) {
                    reservedSub = mainSlot;
                    logd("Enable: reserved sub not set, this time set to " + mainSlot);
                }
                expectPara = getExpectParaCheckEnableTwoSim(hwVSimRequest, reservedSub);
            }
            HwVSimModemAdapter.ExpectPara expectPara2 = expectPara;
            HwVSimSlotSwitchController.CommrilMode expectCommrilMode = expectPara2.getExpectCommrilMode();
            int expectSlot = expectPara2.getExpectSlot();
            setAlternativeUserReservedSubId(expectSlot);
            int i = expectSlot;
            processAfterCheckEnableCondition(processor, hwVSimRequest, expectCommrilMode, expectSlot, currentCommrilMode);
        }
    }

    private HwVSimModemAdapter.ExpectPara getExpectParaCheckEnableNoSim(HwVSimRequest request) {
        HwVSimModemAdapter.ExpectPara expectPara = new HwVSimModemAdapter.ExpectPara();
        boolean isVSimOnM0 = request.getIsVSimOnM0();
        int mainSlot = request.getMainSlot();
        int slaveSlot = HwVSimUtilsInner.getAnotherSlotId(mainSlot);
        HwVSimSlotSwitchController.CommrilMode expectCommrilMode = HwVSimSlotSwitchController.CommrilMode.getCGMode();
        int expectSlot = isVSimOnM0 ? mainSlot : slaveSlot;
        expectPara.setExpectSlot(expectSlot);
        expectPara.setExpectCommrilMode(expectCommrilMode);
        logd("getExpectParaCheckEnableNoSim expectCommrilMode = " + expectCommrilMode + " expectSlot = " + expectSlot);
        return expectPara;
    }

    private HwVSimModemAdapter.ExpectPara getExpectParaCheckEnableOneSim(HwVSimRequest request) {
        HwVSimModemAdapter.ExpectPara expectPara = new HwVSimModemAdapter.ExpectPara();
        boolean isVSimOnM0 = request.getIsVSimOnM0();
        int mainSlot = request.getMainSlot();
        int slaveSlot = HwVSimUtilsInner.getAnotherSlotId(mainSlot);
        HwVSimSlotSwitchController.CommrilMode expectCommrilMode = HwVSimSlotSwitchController.CommrilMode.getCGMode();
        int[] cardTypes = request.getCardTypes();
        int expectSlot = isVSimOnM0 ? mainSlot : slaveSlot;
        for (int i = 0; i < cardTypes.length; i++) {
            if (cardTypes[i] == 0) {
                expectSlot = i;
            }
        }
        expectPara.setExpectSlot(expectSlot);
        expectPara.setExpectCommrilMode(expectCommrilMode);
        logd("getExpectParaCheckEnableOneSim expectCommrilMode = " + expectCommrilMode + " expectSlot = " + expectSlot);
        return expectPara;
    }

    private HwVSimModemAdapter.ExpectPara getExpectParaCheckEnableTwoSim(HwVSimRequest request, int reservedSub) {
        HwVSimModemAdapter.ExpectPara expectPara = new HwVSimModemAdapter.ExpectPara();
        int slotInM1 = reservedSub;
        int slotInM2 = HwVSimUtilsInner.getAnotherSlotId(slotInM1);
        if (this.mVSimController.getSubState(slotInM1) == 0 && this.mVSimController.getSubState(slotInM2) != 0) {
            logd("getExpectParaCheckEnableTwoSim, slot in m1 is inactive, so move to m2.");
            slotInM2 = slotInM1;
        }
        expectPara.setExpectSlot(slotInM2);
        HwVSimSlotSwitchController.CommrilMode expectCommrilMode = HwVSimSlotSwitchController.CommrilMode.HISI_CG_MODE;
        expectPara.setExpectCommrilMode(expectCommrilMode);
        int[] cardTypes = request.getCardTypes();
        if (slotInM2 >= 0 && slotInM2 < cardTypes.length) {
            HwVSimPhoneFactory.setUnReservedSubCardType(cardTypes[slotInM2]);
        }
        logd("getExpectParaCheckEnableTwoSim expectCommrilMode = " + expectCommrilMode + " expectSlot = " + slotInM2);
        return expectPara;
    }

    private void processAfterCheckEnableCondition(HwVSimProcessor processor, HwVSimRequest request, HwVSimSlotSwitchController.CommrilMode expectCommrilMode, int expectSlot, HwVSimSlotSwitchController.CommrilMode currentCommrilMode) {
        logd("Enable: processWhenCheckEnableCondition");
        logd("Enable: expectCommrilMode = " + expectCommrilMode + " expectSlot = " + expectSlot + " currentCommrilMode = " + currentCommrilMode);
        boolean isNeedSwitchCommrilMode = calcIsNeedSwitchCommrilMode(expectCommrilMode, currentCommrilMode);
        StringBuilder sb = new StringBuilder();
        sb.append("Enable: isNeedSwitchCommrilMode = ");
        sb.append(isNeedSwitchCommrilMode);
        logd(sb.toString());
        if (isNeedSwitchCommrilMode) {
            request.setIsNeedSwitchCommrilMode(true);
            request.setExpectCommrilMode(expectCommrilMode);
        }
        request.setExpectSlot(expectSlot);
        int mainSlot = request.getMainSlot();
        boolean isVSimOnM0 = request.getIsVSimOnM0();
        if (expectSlot != mainSlot) {
            processor.setProcessType(HwVSimController.ProcessType.PROCESS_TYPE_CROSS);
            HwVSimEventReport.VSimEventInfoUtils.setPocessType(this.mVSimController.mEventInfo, 2);
        } else if (!isVSimOnM0 || isNeedSwitchCommrilMode) {
            processor.setProcessType(HwVSimController.ProcessType.PROCESS_TYPE_SWAP);
            HwVSimEventReport.VSimEventInfoUtils.setPocessType(this.mVSimController.mEventInfo, 1);
        } else {
            int[] subs = getSimSlotTable();
            if (subs.length == 0) {
                processor.doProcessException(null, request);
                return;
            }
            request.setSubs(subs);
            processor.setProcessType(HwVSimController.ProcessType.PROCESS_TYPE_DIRECT);
            HwVSimEventReport.VSimEventInfoUtils.setPocessType(this.mVSimController.mEventInfo, 4);
        }
        processor.transitionToState(3);
    }

    public void checkDisableSimCondition(HwVSimProcessor processor, HwVSimRequest request) {
        if (request != null) {
            int[] cardTypes = request.getCardTypes();
            if (cardTypes != null && cardTypes.length != 0) {
                int insertedCardCount = HwVSimUtilsInner.getInsertedCardCount(cardTypes);
                logd("Disable: inserted card count = " + insertedCardCount);
                int savedMainSlot = getVSimSavedMainSlot();
                logd("Disable: savedMainSlot = " + savedMainSlot);
                HwVSimSlotSwitchController.CommrilMode currentCommrilMode = getCommrilMode();
                logd("Disable: currentCommrilMode = " + currentCommrilMode);
                int mainSlot = request.getMainSlot();
                logd("Disable: mainSlot = " + mainSlot);
                int expectSlot = getExpectSlotForDisable(cardTypes, mainSlot, savedMainSlot);
                logd("Disable: expectSlot = " + expectSlot);
                request.setExpectSlot(expectSlot);
                HwVSimSlotSwitchController.CommrilMode expectCommrilMode = getVSimOffCommrilMode(expectSlot, cardTypes);
                logd("Disable: expectCommrilMode = " + expectCommrilMode);
                boolean isNeedSwitchCommrilMode = calcIsNeedSwitchCommrilMode(expectCommrilMode, currentCommrilMode);
                logd("Disable: isNeedSwitchCommrilMode = " + isNeedSwitchCommrilMode);
                request.setIsNeedSwitchCommrilMode(isNeedSwitchCommrilMode);
                if (IS_FAST_SWITCH_SIMSLOT && isNeedSwitchCommrilMode) {
                    request.setExpectCommrilMode(expectCommrilMode);
                }
                if (processor.isReadyProcess()) {
                    HwVSimPhoneFactory.setIsVsimEnabledProp(false);
                    getIMSI(expectSlot);
                    processor.transitionToState(0);
                } else {
                    if (expectSlot == mainSlot) {
                        processor.setProcessType(HwVSimController.ProcessType.PROCESS_TYPE_SWAP);
                    } else {
                        processor.setProcessType(HwVSimController.ProcessType.PROCESS_TYPE_CROSS);
                    }
                    processor.transitionToState(6);
                }
            }
        }
    }

    public void radioPowerOff(HwVSimProcessor processor, HwVSimRequest request) {
        if (processor == null || request == null) {
            loge("radioPowerOff, param is null !");
            return;
        }
        request.createPowerOnOffMark();
        request.createGetSimStateMark();
        request.createCardOnOffMark();
        int subCount = request.getSubCount();
        logd("onEnter subCount = " + subCount);
        for (int i = 0; i < subCount; i++) {
            int subId = request.getSubIdByIndex(i);
            if (getCiBySub(subId) == null || !HwVSimUtilsInner.isRadioAvailable(subId)) {
                logd("[2cards]don't operate card in modem2.");
                request.setPowerOnOffMark(i, false);
                request.setSimStateMark(i, false);
                request.setCardOnOffMark(i, false);
                request.setGetIccCardStatusMark(i, false);
                getPhoneBySub(subId).getServiceStateTracker().setDesiredPowerState(false);
            } else {
                request.setPowerOnOffMark(i, true);
                request.setSimStateMark(i, true);
                request.setCardOnOffMark(i, true);
                request.setGetIccCardStatusMark(i, true);
                radioPowerOff(processor, request.clone(), subId);
            }
        }
    }

    public void onRadioPowerOffDone(HwVSimProcessor processor, AsyncResult ar) {
        if (processor == null || ar == null || ar.userObj == null) {
            loge("onRadioPowerOffDone, param is null !");
            return;
        }
        HwVSimRequest request = (HwVSimRequest) ar.userObj;
        int subId = request.mSubId;
        logd("onRadioPowerOffDone : subId = " + subId);
        int subCount = request.getSubCount();
        for (int i = 0; i < subCount; i++) {
            if (subId == request.getSubIdByIndex(i)) {
                request.setPowerOnOffMark(i, false);
                request.setSimStateMark(i, false);
            }
        }
        int simIndex = 1;
        if (subId == 2) {
            simIndex = 11;
        }
        cardPowerOff(processor, request, subId, simIndex);
    }

    public void onSwitchCommrilDone(HwVSimProcessor processor, AsyncResult ar) {
        HwVSimRequest request = (HwVSimRequest) ar.userObj;
        int subId = request.mSubId;
        logd("onSwitchCommrilDone : subId = " + subId);
        int mainSlot = request.getMainSlot();
        int expectSlot = request.getExpectSlot();
        if (!(mainSlot == expectSlot || expectSlot == -1)) {
            logd("onSwitchCommrilDone : adjust mainSlot to " + expectSlot);
            request.setMainSlot(expectSlot);
        }
        processor.setProcessType(HwVSimController.ProcessType.PROCESS_TYPE_SWAP);
    }

    public void switchSimSlot(HwVSimProcessor processor, HwVSimRequest request) {
        int modem1;
        int modem0;
        int modem2;
        int modem12;
        int modem02;
        HwVSimProcessor hwVSimProcessor = processor;
        HwVSimRequest hwVSimRequest = request;
        if (hwVSimProcessor == null || hwVSimRequest == null) {
            loge("switchSimSlot, param is null !");
            return;
        }
        int mainSlot = request.getMainSlot();
        int slaveSlot = HwVSimUtilsInner.getAnotherSlotId(mainSlot);
        int subId = mainSlot;
        boolean isSwap = processor.isSwapProcess();
        if (processor.isEnableProcess()) {
            logd("switchSimSlot, enable!");
            modem2 = request.getExpectSlot();
            modem0 = HwVSimUtilsInner.getAnotherSlotId(modem2);
            modem1 = 2;
            if (request.getIsVSimOnM0()) {
                subId = 2;
            }
        } else if (processor.isDisableProcess() != 0) {
            logd("switchSimSlot, disable !");
            int savedMainSlot = request.getExpectSlot();
            if (isSwap) {
                modem02 = mainSlot;
                modem12 = slaveSlot;
            } else if (savedMainSlot == 0) {
                modem02 = 0;
                modem12 = 1;
            } else {
                modem02 = 1;
                modem12 = 0;
            }
            subId = 2;
            logd("switchSimSlot, main slot set to dds: " + modem02);
            SubscriptionController.getInstance().setDataSubId(modem02);
            modem2 = 2;
            int i = modem12;
            modem1 = modem02;
            modem0 = i;
        } else {
            hwVSimProcessor.doProcessException(null, hwVSimRequest);
            return;
        }
        int[] oldSlots = getSimSlotTable();
        int[] slots = createSimSlotsTable(modem1, modem0, modem2);
        hwVSimRequest.setSlots(slots);
        hwVSimRequest.mSubId = subId;
        boolean z = true;
        if (oldSlots.length == 3 && oldSlots[0] == slots[0] && oldSlots[1] == slots[1] && oldSlots[2] == slots[2]) {
            z = false;
        }
        boolean needSwich = z;
        Message onCompleted = hwVSimProcessor.obtainMessage(43, hwVSimRequest);
        if (needSwich) {
            logd("switchSimSlot subId " + subId + " modem0 = " + modem1 + " modem1 = " + modem0 + " modem2 = " + modem2);
            CommandsInterface ci = getCiBySub(subId);
            if (ci != null) {
                ci.hotSwitchSimSlot(modem1, modem0, modem2, onCompleted);
            }
        } else {
            logd("switchSimSlot return success");
            AsyncResult.forMessage(onCompleted, null, null);
            onCompleted.sendToTarget();
        }
    }

    public void onSwitchSlotDone(HwVSimProcessor processor, AsyncResult ar) {
        HwVSimRequest request = (HwVSimRequest) ar.userObj;
        int[] slots = request.getSlots();
        if (slots == null) {
            processor.doProcessException(null, request);
            return;
        }
        setSimSlotTable(slots);
        HwVSimPhoneFactory.setPropPersistRadioSimSlotCfg(slots);
    }

    public void setActiveModemMode(HwVSimProcessor processor, HwVSimRequest request, int subId) {
        request.mSubId = subId;
        logd("setActiveModemMode, subId = " + subId);
        Message onCompleted = processor.obtainMessage(47, request);
        CommandsInterface ci = getCiBySub(subId);
        if (ci == null) {
            AsyncResult.forMessage(onCompleted);
            onCompleted.sendToTarget();
        } else if (processor.isEnableProcess()) {
            ci.setActiveModemMode(1, onCompleted);
        } else if (processor.isDisableProcess()) {
            if (request.getCardCount() == 1) {
                ci.setActiveModemMode(0, onCompleted);
            } else {
                ci.setActiveModemMode(1, onCompleted);
            }
        } else if (processor.isSwitchModeProcess()) {
            ci.setActiveModemMode(1, onCompleted);
        } else {
            AsyncResult.forMessage(onCompleted);
            onCompleted.sendToTarget();
        }
    }

    public int getPoffSubForEDWork(HwVSimRequest request) {
        if (request == null) {
            return -1;
        }
        request.mSubId = 2;
        return 2;
    }

    public void getModemSupportVSimVersion(HwVSimProcessor processor, int subId) {
        Message onCompleted = processor.obtainMessage(73, null);
        logd("start to get modem support vsim version.");
        onCompleted.sendToTarget();
    }

    public void onGetModemSupportVSimVersionDone(HwVSimProcessor processor, AsyncResult ar) {
        this.mVSimController.setModemVSimVersion(-2);
        logd("modem support vsim version is: -2");
    }

    public void getModemSupportVSimVersionInner(HwVSimProcessor processor, HwVSimRequest request) {
        Message onCompleted = processor.obtainMessage(30, request);
        logd("start to get modem support vsim version for inner.");
        AsyncResult.forMessage(onCompleted).exception = new CommandException(CommandException.Error.REQUEST_NOT_SUPPORTED);
        onCompleted.sendToTarget();
    }

    public boolean isNeedRadioOnM2() {
        return false;
    }

    public void onSetNetworkRatAndSrvdomainDone(HwVSimProcessor processor, AsyncResult ar) {
    }

    public void doEnableStateEnter(HwVSimProcessor processor, HwVSimRequest request) {
    }

    public void doDisableStateExit(HwVSimProcessor processor, HwVSimRequest request) {
    }

    public void onEDWorkTransitionState(HwVSimProcessor processor) {
        if (processor != null) {
            processor.transitionToState(0);
        }
    }

    /* access modifiers changed from: protected */
    public void logd(String s) {
        HwVSimLog.VSimLogD(LOG_TAG, s);
    }

    /* access modifiers changed from: protected */
    public void loge(String s) {
        HwVSimLog.VSimLogE(LOG_TAG, s);
    }

    private int[] createSimSlotsTable(int m0, int m1, int m2) {
        int[] slots = new int[MAX_SUB_COUNT];
        slots[0] = m0;
        slots[1] = m1;
        slots[2] = m2;
        return slots;
    }

    public void checkSwitchModeSimCondition(HwVSimProcessor processor, HwVSimRequest request) {
    }

    public void getSimState(HwVSimProcessor processor, HwVSimRequest request) {
    }

    private void setAlternativeUserReservedSubId(int expectSlot) {
        int slotInM1 = HwVSimUtilsInner.getAnotherSlotId(expectSlot);
        this.mVSimController.setAlternativeUserReservedSubId(slotInM1);
        logd("setAlternativeUserReservedSubId: set subId is " + slotInM1 + ".");
    }

    private HwVSimSlotSwitchController.CommrilMode getVSimOffCommrilMode(int mainSlot, int[] cardTypes) {
        HwVSimSlotSwitchController.CommrilMode vSimOffCommrilMode;
        int slaveSlot = mainSlot == 0 ? 1 : 0;
        boolean mainSlotIsCDMACard = HwVSimSlotSwitchController.isCDMACard(cardTypes[mainSlot]);
        boolean slaveSlotIsCDMACard = HwVSimSlotSwitchController.isCDMACard(cardTypes[slaveSlot]);
        if (mainSlotIsCDMACard && slaveSlotIsCDMACard) {
            vSimOffCommrilMode = HwVSimSlotSwitchController.CommrilMode.HISI_CGUL_MODE;
        } else if (mainSlotIsCDMACard) {
            vSimOffCommrilMode = HwVSimSlotSwitchController.CommrilMode.HISI_CGUL_MODE;
        } else if (slaveSlotIsCDMACard) {
            vSimOffCommrilMode = HwVSimSlotSwitchController.CommrilMode.HISI_CG_MODE;
        } else {
            vSimOffCommrilMode = getCurrentCommrilMode();
            logd("no c-card, not change commril mode. vSimOnCommrilMode = " + vSimOffCommrilMode);
        }
        logd("getVSimOffCommrilMode: mainSlot = " + mainSlot + ", cardTypes = " + Arrays.toString(cardTypes) + ", mode = " + vSimOffCommrilMode);
        return vSimOffCommrilMode;
    }

    private HwVSimSlotSwitchController.CommrilMode getCurrentCommrilMode() {
        String mode = SystemProperties.get("persist.radio.commril_mode", "HISI_CGUL_MODE");
        HwVSimSlotSwitchController.CommrilMode result = HwVSimSlotSwitchController.CommrilMode.NON_MODE;
        try {
            return (HwVSimSlotSwitchController.CommrilMode) Enum.valueOf(HwVSimSlotSwitchController.CommrilMode.class, mode);
        } catch (IllegalArgumentException e) {
            logd("getCommrilMode, IllegalArgumentException, mode = " + mode);
            return result;
        }
    }

    public void onSimHotPlugOut() {
        HwVSimPhoneFactory.setUnReservedSubCardType(-1);
    }

    public void onRadioPowerOffSlaveModemDone(HwVSimProcessor processor, HwVSimRequest request) {
        if (processor.isEnableProcess()) {
            int slotIdInModem1 = request.mSubId;
            int slotIndex = slotIdInModem1 == 2 ? 11 : 1;
            logd("onRadioPowerOffSlaveModemDone, slotIdInModem1 = " + slotIdInModem1);
            cardPowerOff(slotIdInModem1, slotIndex, null);
            return;
        }
        logd("onRadioPowerOffSlaveModemDone, do nothing.");
    }

    public void onCardPowerOffDoneInEWork(HwVSimProcessor processor, int subId) {
        if (processor.isEnableProcess() && processor.isWorkProcess()) {
            int unReservedSlotId = HwVSimUtilsInner.getAnotherSlotId(this.mVSimController.getUserReservedSubId());
            if (subId == unReservedSlotId) {
                logd("onCardPowerOffDoneInEWork, will dispose card " + subId);
                this.mVSimController.disposeCard(unReservedSlotId);
                return;
            }
            logd("onCardPowerOffDoneInEWork, not dispose card " + subId);
        }
    }

    public int getAllAbilityNetworkTypeOnModem1(boolean duallteCapOpened) {
        if (duallteCapOpened) {
            return 9;
        }
        return 3;
    }
}
