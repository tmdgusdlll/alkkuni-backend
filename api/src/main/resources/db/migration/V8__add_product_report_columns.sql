-- I0030에서 화면·필터에 쓸 값을 추가로 받는다. 모두 원문 그대로 둔다.
-- form: 제형(PRDT_SHAP_CD_NM, 정·캡슐·분말 등 14종). 제형 아이콘과 제형 필터에 쓴다.
-- reported_on: 품목제조 신고일(PRMS_DT). "최신 신고순" 정렬에 쓴다. source_updated_at은 수정일이다.
-- functional_materials: 기능성 원료 목록(INDIV_RAWMTRL_NM). 단일/복합 원료 필터에 쓴다.
-- functionality: 기능성 원문(PRIMARY_FNCLTY). 자유 문장이라 기능성 카테고리 사전의 입력으로만 쓴다.
-- precautions: 섭취 시 주의사항(IFTKN_ATNT_MATR_CN). 제품 상세에 원문 그대로 보여 준다.
ALTER TABLE product
    ADD COLUMN form                 VARCHAR(30),
    ADD COLUMN reported_on          DATE,
    ADD COLUMN functional_materials TEXT,
    ADD COLUMN functionality        TEXT,
    ADD COLUMN precautions          TEXT;
