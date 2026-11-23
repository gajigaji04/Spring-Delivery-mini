import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { App } from './App';
import { serverWake } from './api/client';
import { AuthProvider } from './auth/AuthContext';
import { CartProvider } from './cart/CartContext';
import { ServerWakeBanner } from './components/ServerWakeBanner';
import { ToastProvider } from './components/Toast';
import { OwnerProvider } from './owner/OwnerContext';
import './styles/tokens.css';
import './styles/base.css';
import './styles/owner.css';

// 첫 API 요청(로그인 복구 등)보다 먼저 서버가 응답하는지 확인 — 절전 중이면 깨어날 때까지 요청을 미루고 안내 배너 표시
void serverWake.ensureAwake();

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <ServerWakeBanner />
      <ToastProvider>
        <AuthProvider>
          <CartProvider>
            <OwnerProvider>
              <App />
            </OwnerProvider>
          </CartProvider>
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  </StrictMode>,
);

// 웹폰트 CSS는 첫 화면을 그린 뒤에 받음 — 한글 @font-face가 많아 CSS가 크므로 렌더링을 막지 않게 (font-display: swap)
void import('./styles/fonts');
