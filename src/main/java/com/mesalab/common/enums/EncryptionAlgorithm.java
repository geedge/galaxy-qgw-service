package com.mesalab.common.enums;

/**
 * TODO
 *
 * @Classname EncryptionAlgorithm
 * @Date 2024/10/21 14:36
 * @Author wWei
 */
public enum EncryptionAlgorithm {
    /**
     * Desc: encryption algorithms
     */
    AES_128_GCM("aes-128-gcm"),
    AES_128_GCM96("aes-128-gcm96"),
    AES_256_GCM96("aes-256-gcm96"),
    SM4_GCM96("sm4-gcm96");
    private final String value;

    EncryptionAlgorithm(String value) {
        this.value = value.toLowerCase();
    }

    public String getValue() {
        return value;
    }

    public static boolean isValid(String algorithm) {
        for (EncryptionAlgorithm algo : values()) {
            if (algo.getValue().equals(algorithm)) {
                return true;
            }
        }
        return false;
    }
}
