package com.jeongjungang.ui.meeting;

import com.jeongjungang.data.remote.meeting.MeetingModels.InvitePreview;
import com.jeongjungang.data.remote.meeting.MeetingModels.Membership;

/**
 * 버튼 동작 한 번의 결과(약속 만들기, 참가, 수정, 나가기 등).
 * {@link MeetingViewModel#getAction()}으로 관찰하고, 처리했으면 {@link MeetingViewModel#consumeAction()}을 부른다
 * (화면 회전 뒤에 같은 토스트가 다시 뜨지 않게).
 */
public final class ActionResult {

    /** 문구 대신 오류 코드로 화면을 분기한다. 기존 호출부는 null을 사용한다. */
    public final com.jeongjungang.data.remote.ApiException error;

    public enum Kind { CREATE, PREVIEW, JOIN, UPDATE_MEETING, UPDATE_ME, LEAVE, CANCEL, KICK }

    public enum Status { RUNNING, DONE, FAILED }

    public final Kind kind;
    public final Status status;
    /** FAILED일 때 사용자에게 보여 줄 문장. */
    public final String message;
    /** CREATE·JOIN 성공 시. CREATE면 membership.meeting.inviteUrl로 초대 링크를 공유한다. */
    public final Membership membership;
    /** PREVIEW 성공 시. */
    public final InvitePreview preview;

    private ActionResult(Kind kind, Status status, String message, Membership membership, InvitePreview preview) {
        this(kind, status, message, membership, preview, null);
    }

    private ActionResult(Kind kind, Status status, String message, Membership membership,
            InvitePreview preview, com.jeongjungang.data.remote.ApiException error) {
        this.error = error;
        this.kind = kind;
        this.status = status;
        this.message = message;
        this.membership = membership;
        this.preview = preview;
    }

    static ActionResult running(Kind kind) {
        return new ActionResult(kind, Status.RUNNING, null, null, null);
    }

    static ActionResult done(Kind kind) {
        return new ActionResult(kind, Status.DONE, null, null, null);
    }

    static ActionResult joined(Kind kind, Membership membership) {
        return new ActionResult(kind, Status.DONE, null, membership, null);
    }

    static ActionResult previewed(InvitePreview preview) {
        return new ActionResult(Kind.PREVIEW, Status.DONE, null, null, preview);
    }

    static ActionResult failed(Kind kind, String message) {
        return new ActionResult(kind, Status.FAILED, message, null, null);
    }

    static ActionResult failed(Kind kind, com.jeongjungang.data.remote.ApiException error) {
        return new ActionResult(kind, Status.FAILED, error.getMessage(), null, null, error);
    }
}
