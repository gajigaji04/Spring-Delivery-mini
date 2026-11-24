import { useCallback, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { menuApi, orderApi } from '../api/endpoints';
import { DEMO_ADDRESS, validateDeliveryRequest } from '../address/orderExtras';
import { normalizeAddress, validateAddress, type AddressErrors, type AddressInput } from '../address/validateAddress';
import { useCart } from '../cart/CartContext';
import { checkCartMenus } from '../cart/cartValidation';
import { AddressFields } from '../components/AddressFields';
import { DeliveryRequestField } from '../components/DeliveryRequestField';
import { IconInfo } from '../components/Icons';
import { ErrorMessage } from '../components/ErrorMessage';
import { Money } from '../components/format';
import { useAsync } from '../hooks/useAsync';

export default function CartPage() {
  const cart = useCart();
  const navigate = useNavigate();
  // 기본은 체험용 주소 — 데모 서비스에 실제 집 주소가 저장되지 않도록. 실제 주소는 '주소 검색'을 골랐을 때만
  const [mode, setMode] = useState<'demo' | 'search'>('demo');
  const [address, setAddress] = useState<AddressInput>({ zipCode: '', roadAddress: '', detailAddress: '' });
  const [deliveryRequest, setDeliveryRequest] = useState('');
  const requestError = validateDeliveryRequest(deliveryRequest);
  // 제출을 한 번 시도한 뒤부터 입력할 때마다 다시 검사 (처음부터 빨간 안내를 띄우지 않도록)
  const [touched, setTouched] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const target = mode === 'demo' ? DEMO_ADDRESS : address;
  const addressErrors: AddressErrors = touched && mode === 'search' ? validateAddress(address) : {};
  const onAddressChange = useCallback((next: AddressInput) => setAddress(next), []);
  const [submitting, setSubmitting] = useState(false);
  // 담은 메뉴가 아직 판매 중인지 서버에 다시 확인 — 데모 데이터 초기화·메뉴 삭제를 브라우저 장바구니는 모름 (cart/cartValidation)
  // 수량만 바뀔 때는 다시 묻지 않도록 메뉴 구성(id·매장)이 바뀔 때만
  const menuKey = cart.lines.map((l) => `${l.menu.id}:${l.menu.storeId}`).join(',');
  const check = useAsync(
    () => (cart.lines.length > 0 ? checkCartMenus(cart.lines, menuApi.get) : Promise.resolve(null)),
    [menuKey],
  );
  const staleIds = check.data?.staleIds ?? [];
  const staleNames = cart.lines.filter((l) => staleIds.includes(l.menu.id)).map((l) => l.menu.name);

  if (cart.lines.length === 0) {
    return (
      <section>
        <h1>장바구니</h1>
        <p className="notice">담은 메뉴가 없습니다.</p>
      </section>
    );
  }

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setTouched(true);
    if (Object.keys(validateAddress(target)).length > 0 || requestError) return;
    setSubmitting(true);
    setError(null);
    try {
      const order = await orderApi.create(
        cart.lines.map((l) => ({ menuId: l.menu.id, quantity: l.quantity })),
        normalizeAddress(target),
        deliveryRequest.trim(),
      );
      cart.clear();
      // 주문 생성 후 결제는 주문 상세에서 (주문요청 → 결제 → 결제완료)
      navigate(`/orders/${order.id}`);
    } catch (err) {
      // 서버 주소 검증 실패(INVALID_ADDRESS)는 주소 칸 안내도 함께 보이게
      if (err instanceof ApiError && err.code === 'INVALID_ADDRESS') setTouched(true);
      // 확인 뒤에 메뉴가 사라진 경우 — 다시 확인해 어떤 메뉴인지 표시
      if (err instanceof ApiError && err.code === 'MENU_NOT_FOUND') check.reload();
      setError(err);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <section>
      <h1>장바구니</h1>
      {staleIds.length > 0 && check.data && (
        <div className="card" role="alert">
          <p>
            판매가 끝난 메뉴가 있어요: <b>{staleNames.join(', ')}</b>
          </p>
          <p className="muted">매장에서 메뉴를 내렸거나 데모 데이터가 초기화됐을 수 있어요. 빼고 나면 주문할 수 있어요.</p>
          <button type="button" className="btn" onClick={() => cart.applyCheck(staleIds, check.data!.current)}>
            판매 종료 메뉴 빼기
          </button>
        </div>
      )}
      <ul className="list">
        {cart.lines.map((l) => (
          <li key={l.menu.id} className="card row" style={{ justifyContent: 'space-between' }}>
            <span>
              {l.menu.name} · <Money value={l.menu.price} />
              {staleIds.includes(l.menu.id) && <span className="muted"> · 판매 종료</span>}
            </span>
            <span className="row">
              <button type="button" className="btn btn--ghost" onClick={() => cart.setQuantity(l.menu.id, l.quantity - 1)}>
                −
              </button>
              <span className="num">{l.quantity}</span>
              <button type="button" className="btn btn--ghost" onClick={() => cart.setQuantity(l.menu.id, l.quantity + 1)}>
                +
              </button>
            </span>
          </li>
        ))}
      </ul>
      <p>
        합계 <Money value={cart.total} /> <span className="muted">(최종 금액은 서버가 계산)</span>
      </p>
      <form className="form" onSubmit={onSubmit}>
        <div className="amode">
          <span className="amode__label" id="address-mode-label">
            배달 주소
          </span>
          <div className="seg" role="group" aria-labelledby="address-mode-label">
            <button type="button" className={mode === 'demo' ? 'on' : ''} aria-pressed={mode === 'demo'} onClick={() => setMode('demo')}>
              체험용 주소
            </button>
            <button type="button" className={mode === 'search' ? 'on' : ''} aria-pressed={mode === 'search'} onClick={() => setMode('search')}>
              주소 검색
            </button>
          </div>
          {mode === 'demo' ? (
            <div className="amode__demo">
              <b>
                <span className="num">({DEMO_ADDRESS.zipCode})</span> {DEMO_ADDRESS.roadAddress}
              </b>
              <p>
                <IconInfo size={16} /> 데모 서비스라 실제로 배달되지 않아요. 개인정보 보호를 위해 실제 집 주소 대신 이 주소로 주문해
                보세요.
              </p>
            </div>
          ) : (
            <>
              <p className="amode__warn">
                <IconInfo size={16} /> 입력한 주소는 주문 기록에 저장되고 매장에 보여요. 데모에서는 실제 집 주소를 넣지 마세요.
              </p>
              <AddressFields value={address} onChange={onAddressChange} errors={addressErrors} />
            </>
          )}
        </div>
        <DeliveryRequestField value={deliveryRequest} onChange={setDeliveryRequest} error={touched ? requestError : null} />
        <ErrorMessage error={error} />
        <button className="btn" disabled={submitting || staleIds.length > 0}>
          주문하기
        </button>
      </form>
    </section>
  );
}
