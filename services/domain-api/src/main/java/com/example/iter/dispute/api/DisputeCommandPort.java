package com.example.iter.dispute.api;

// dispute 가 다른 도메인에게 공개하는 분쟁 생성 창구.
public interface DisputeCommandPort {

    // 반납 확인 과정에서 문제가 제기되어 분쟁을 연다. 생성된 분쟁 ID 를 돌려준다.
    //
    // 이전에는 reservation 이 Dispute 엔티티를 빌더로 직접 조립해 저장했다.
    // 엔티티 조립 규칙(필수 필드, 초기 상태)이 남의 도메인 코드에 박혀 있으면
    // dispute 가 그 규칙을 바꿀 때 reservation 도 같이 고쳐야 한다.
    Long openReturnDispute(ReturnDisputeCommand command);
}
