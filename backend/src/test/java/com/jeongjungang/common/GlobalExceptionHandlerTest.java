package com.jeongjungang.common;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jeongjungang.WebSliceTestConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.ProbeController.class)
@Import({GlobalExceptionHandlerTest.ProbeController.class, WebSliceTestConfig.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mvc;

    @RestController
    @RequestMapping("/probe")
    static class ProbeController {

        record NameBody(@NotBlank String name) {}

        @GetMapping("/api-error")
        void apiError() {
            throw new ApiException(ErrorCode.MEETING_NOT_FOUND);
        }

        @GetMapping("/upstream")
        void upstream() {
            throw new ApiException(ErrorCode.UPSTREAM_ERROR, "kakao responded 500: secret-detail", null);
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("db password is hunter2");
        }

        @PostMapping("/body")
        void body(@Valid @RequestBody NameBody body) {}

        @GetMapping("/param")
        void param(@RequestParam @Size(min = 2, max = 100) String query) {}

        @GetMapping("/number")
        void number(@RequestParam int size) {}
    }

    @Test
    void apiException_usesItsCodeStatusAndMessage() throws Exception {
        mvc.perform(get("/probe/api-error"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.error.code").value("MEETING_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value(ErrorCode.MEETING_NOT_FOUND.message()));
    }

    @Test
    void upstreamError_hidesTheInternalDetail() throws Exception {
        mvc.perform(get("/probe/upstream"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("UPSTREAM_ERROR"))
                .andExpect(content().string(not(containsString("secret-detail"))));
    }

    @Test
    void unexpectedException_returnsGenericInternalError() throws Exception {
        mvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("hunter2"))));
    }

    @Test
    void invalidBody_listsTheFailingField() throws Exception {
        mvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fields.name").exists());
    }

    @Test
    void malformedJson_isAValidationFailure() throws Exception {
        mvc.perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void invalidRequestParam_listsTheFailingParameter() throws Exception {
        mvc.perform(get("/probe/param").param("query", "a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fields.query").exists());
    }

    @Test
    void missingRequestParam_isAValidationFailure() throws Exception {
        mvc.perform(get("/probe/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fields.query").exists());
    }

    @Test
    void wrongParamType_isAValidationFailure() throws Exception {
        mvc.perform(get("/probe/number").param("size", "ten"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fields.size").exists());
    }

    @Test
    void unknownPath_isNotFound() throws Exception {
        mvc.perform(get("/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void wrongMethod_isMethodNotAllowed() throws Exception {
        mvc.perform(post("/probe/api-error"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void successResponse_hasNullError() {
        ApiResponse<String> ok = ApiResponse.ok("hi");
        org.assertj.core.api.Assertions.assertThat(ok.success()).isTrue();
        org.assertj.core.api.Assertions.assertThat(ok.data()).isEqualTo("hi");
        org.assertj.core.api.Assertions.assertThat(ok.error()).isNull();
    }
}
