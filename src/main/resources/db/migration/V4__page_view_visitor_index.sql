-- 같은 사람이 30분 안에 같은 페이지를 다시 열었는지 빠르게 확인한다 (새로고침 중복 제거)
CREATE INDEX idx_page_view_visitor ON page_view (visitor, viewed_at);
