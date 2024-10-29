package com.mesalab.qgw.model.job;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author wangwei
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DataDecryptionRequest {
    /**
     * A list of ciphertext to be decrypted
     */
    @JsonProperty("ciphertext_list")
    private List<String> ciphertextList;
    /**
     * The default is aes-128-gcm96 Supported symmetric encryption algorithms. Include aes-128-gcm, aes-128-gcm96, aes-256-gcm96, sm4-gcm96.
     */
    @JsonProperty("encryption_algorithm")
    private String encryptionAlgorithm;
}
