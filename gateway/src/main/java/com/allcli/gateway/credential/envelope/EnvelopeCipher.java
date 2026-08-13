package com.allcli.gateway.credential.envelope;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Component;

/**
 * 国密 SM4 信封加密（一期 Vault 软实现，D11=C）。
 *
 * <p>信封结构：明文由随机 DEK（SM4）加密；DEK 由 KEK（SM4 主密钥）加密。
 * 密文引用格式：{@code allcli-vault://dek/{base64(wrappedDek)}#{base64(ciphertext)}}，
 * 其中每个加密块均为「IV(16B) + 密文」。KEK 一期从配置读固定 hex（16B），生产换行内 KMS。
 */
@Component
public class EnvelopeCipher {

    private static final String TRANSFORMATION = "SM4/CBC/PKCS7Padding";
    private static final String ALGORITHM = "SM4";
    private static final String PREFIX = "allcli-vault://dek/";
    private static final SecureRandom RNG = new SecureRandom();
    private static final int IV_LEN = 16;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /** 用 KEK 包装明文，返回密文引用。 */
    public String encrypt(String plaintext, String kekHex) {
        byte[] kek = hexToBytes(kekHex);
        byte[] dek = new byte[16]; // SM4 128-bit
        RNG.nextBytes(dek);

        byte[] wrappedDek = seal(kek, dek);
        byte[] ciphertext = seal(dek, plaintext.getBytes(StandardCharsets.UTF_8));

        return PREFIX
                + Base64.getEncoder().encodeToString(wrappedDek)
                + "#"
                + Base64.getEncoder().encodeToString(ciphertext);
    }

    /** 从密文引用解出明文。 */
    public String decrypt(String ref, String kekHex) {
        if (ref == null || !ref.startsWith(PREFIX)) {
            return ref; // 非信封格式：当明文返回（兼容占位/旧数据）
        }
        String body = ref.substring(PREFIX.length());
        int sep = body.indexOf('#');
        if (sep < 0) {
            throw new IllegalArgumentException("非法密文引用: " + ref);
        }
        byte[] kek = hexToBytes(kekHex);
        byte[] wrappedDek = Base64.getDecoder().decode(body.substring(0, sep));
        byte[] ciphertext = Base64.getDecoder().decode(body.substring(sep + 1));

        byte[] dek = open(kek, wrappedDek);
        byte[] plain = open(dek, ciphertext);
        return new String(plain, StandardCharsets.UTF_8);
    }

    /** 加密：输出 = IV(16) + 密文。 */
    private byte[] seal(byte[] key, byte[] data) {
        try {
            byte[] iv = new byte[IV_LEN];
            RNG.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, ALGORITHM), new IvParameterSpec(iv));
            byte[] ct = cipher.doFinal(data);
            byte[] out = new byte[IV_LEN + ct.length];
            System.arraycopy(iv, 0, out, 0, IV_LEN);
            System.arraycopy(ct, 0, out, IV_LEN, ct.length);
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("SM4 加密失败: " + e.getMessage(), e);
        }
    }

    /** 解密：输入 = IV(16) + 密文。 */
    private byte[] open(byte[] key, byte[] ivAndCipher) {
        try {
            if (ivAndCipher.length < IV_LEN) {
                throw new IllegalArgumentException("密文块过短");
            }
            byte[] iv = new byte[IV_LEN];
            byte[] ct = new byte[ivAndCipher.length - IV_LEN];
            System.arraycopy(ivAndCipher, 0, iv, 0, IV_LEN);
            System.arraycopy(ivAndCipher, IV_LEN, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, ALGORITHM), new IvParameterSpec(iv));
            return cipher.doFinal(ct);
        } catch (Exception e) {
            throw new IllegalStateException("SM4 解密失败: " + e.getMessage(), e);
        }
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }
}
