package maple.underground_quest.model;

/**
 * 任務獎勵類型。reward 區段的 type 決定 payload 的含義。
 */
public enum QuestRewardType {
    /** 純聊天訊息獎勵，payload = 訊息文字。 */
    MESSAGE,
    /** 給予原版物品，payload = Material 名稱，amount = 數量。 */
    ITEM,
    /** 執行控制台指令，payload = 指令文字（不含斜線）。 */
    COMMAND,
    /** 給予經驗等級，amount = 等級數。 */
    EXPERIENCE,
    /** 給予金錢（走 underground_state 的 BankManager），amount = 金額。 */
    MONEY
}