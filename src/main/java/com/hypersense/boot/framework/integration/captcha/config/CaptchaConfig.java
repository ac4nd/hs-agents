package com.hypersense.boot.framework.integration.captcha.config;

import cn.hutool.captcha.generator.CodeGenerator;
import cn.hutool.captcha.generator.MathGenerator;
import cn.hutool.captcha.generator.RandomGenerator;
import com.hypersense.boot.framework.captcha.CaptchaProperties;
import org.springframework.beans.factory.annotation.Autowired;

import java.awt.*;

/**
 * 验证码自动装配配置
 *
 * @author haoxr
 * @since 2023/11/24
 */
public class CaptchaConfig {

    @Autowired
    private CaptchaProperties captchaProperties;

    /**
     * 验证码文字生成器
     *
     * @return CodeGenerator
     */
    public CodeGenerator codeGenerator() {
        String codeType = captchaProperties.getCode().getType();
        int codeLength = captchaProperties.getCode().getLength();
        if ("math".equalsIgnoreCase(codeType)) {
            return new MathGenerator(codeLength, false);
        } else if ("random".equalsIgnoreCase(codeType)) {
            return new RandomGenerator(codeLength);
        } else {
            throw new IllegalArgumentException("Invalid captcha codegen type: " + codeType);
        }
    }

    /**
     * 验证码字体
     */
    public Font captchaFont() {
        String fontName = captchaProperties.getFont().getName();
        int fontSize = captchaProperties.getFont().getSize();
        int fontWight = captchaProperties.getFont().getWeight();
        return new Font(fontName, fontWight, fontSize);
    }


}
