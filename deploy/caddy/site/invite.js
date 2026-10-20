// 초대 안내 페이지(invite.html). 주소 /m/{코드}에서 코드를 읽어 약속 미리보기를 보여 준다.
// 서버 응답은 textContent로만 넣는다(약속 이름·닉네임은 사용자가 입력한 값이라서)
(function () {
  var STATUS_TEXT = { OPEN: '참가자를 모으는 중', CONFIRMED: '장소가 정해진 약속' };

  var code = decodeURIComponent(location.pathname.replace(/^\/m\//, '').split('/')[0] || '');
  var title = document.getElementById('title');
  var meta = document.getElementById('meta');

  function show(id) {
    document.getElementById(id).hidden = false;
  }

  function formatMeetAt(iso) {
    var date = new Date(iso);
    if (isNaN(date.getTime())) return '';
    return date.toLocaleString('ko-KR', {
      timeZone: 'Asia/Seoul', month: 'long', day: 'numeric', weekday: 'short',
      hour: 'numeric', minute: '2-digit'
    });
  }

  function render(preview) {
    title.textContent = preview.title || '정중앙 약속';
    var lines = [preview.hostNickname + '님이 초대했어요 · ' + preview.participantCount + '명 참가 중'];
    if (preview.meetAt) lines.push(formatMeetAt(preview.meetAt));
    if (STATUS_TEXT[preview.status]) lines.push(STATUS_TEXT[preview.status]);
    meta.textContent = lines.join('\n');
    meta.style.whiteSpace = 'pre-line';
    document.getElementById('code').textContent = code.toUpperCase();
    show('code-box');
    show('help');
  }

  function fail(message) {
    title.textContent = '초대를 열 수 없어요';
    meta.textContent = message;
  }

  document.getElementById('copy').addEventListener('click', function (event) {
    var button = event.currentTarget;
    if (!navigator.clipboard) return;
    navigator.clipboard.writeText(location.href).then(function () {
      button.textContent = '복사됨';
    }, function () {
      button.textContent = '복사 실패';
    });
  });

  if (!code) {
    fail('초대 링크가 올바르지 않아요. 받은 링크를 다시 확인해 주세요.');
    return;
  }

  fetch('/api/v1/meetings/by-code/' + encodeURIComponent(code), { headers: { Accept: 'application/json' } })
    .then(function (response) {
      return response.json().catch(function () { return null; });
    })
    .then(function (body) {
      if (body && body.success && body.data) {
        render(body.data);
      } else {
        fail((body && body.error && body.error.message) || '잠시 후 다시 시도해 주세요.');
      }
    })
    .catch(function () {
      fail('네트워크에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.');
    });
})();
