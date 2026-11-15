import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router';
import { RequireAdmin, RequireAuth, RequireGuest } from './auth/RouteGuards';
import { AppLayout, AuthLayout } from './components/AppLayout';
import { LoadingState } from './components/EmptyState';
import { RealtimeProvider } from './realtime/RealtimeProvider';

const AdminReportsPage = lazy(() => import('./pages/AdminReportsPage').then((m) => ({ default: m.AdminReportsPage })));
const BrowsePage = lazy(() => import('./pages/BrowsePage').then((m) => ({ default: m.BrowsePage })));
const DealDetailPage = lazy(() => import('./pages/DealDetailPage').then((m) => ({ default: m.DealDetailPage })));
const DealsPage = lazy(() => import('./pages/DealsPage').then((m) => ({ default: m.DealsPage })));
const LoginPage = lazy(() => import('./pages/LoginPage').then((m) => ({ default: m.LoginPage })));
const MessagesPage = lazy(() => import('./pages/MessagesPage').then((m) => ({ default: m.MessagesPage })));
const MyOffersPage = lazy(() => import('./pages/MyOffersPage').then((m) => ({ default: m.MyOffersPage })));
const MyPostingsPage = lazy(() => import('./pages/MyPostingsPage').then((m) => ({ default: m.MyPostingsPage })));
const NotFoundPage = lazy(() => import('./pages/NotFoundPage').then((m) => ({ default: m.NotFoundPage })));
const PostingDetailPage = lazy(() =>
  import('./pages/PostingDetailPage').then((m) => ({ default: m.PostingDetailPage })),
);
const PostingEditorPage = lazy(() =>
  import('./pages/PostingEditorPage').then((m) => ({ default: m.PostingEditorPage })),
);
const SettingsPage = lazy(() => import('./pages/SettingsPage').then((m) => ({ default: m.SettingsPage })));
const SocialCallbackPage = lazy(() =>
  import('./pages/SocialCallbackPage').then((m) => ({ default: m.SocialCallbackPage })),
);
const ProfilePage = lazy(() => import('./pages/ProfilePage').then((m) => ({ default: m.ProfilePage })));
const SignupPage = lazy(() => import('./pages/SignupPage').then((m) => ({ default: m.SignupPage })));

export function App() {
  return (
    <Suspense fallback={<LoadingState />}>
      <Routes>
        <Route
          element={
            <RequireGuest>
              <AuthLayout />
            </RequireGuest>
          }
        >
          <Route path="/login" element={<LoginPage />} />
          <Route path="/signup" element={<SignupPage />} />
        </Route>
        <Route
          element={
            <RequireAuth>
              <RealtimeProvider>
                <AppLayout />
              </RealtimeProvider>
            </RequireAuth>
          }
        >
          <Route path="/" element={<Navigate to="/postings" replace />} />
          <Route path="/postings" element={<BrowsePage />} />
          <Route path="/postings/new" element={<PostingEditorPage />} />
          <Route path="/postings/:id" element={<PostingDetailPage />} />
          <Route path="/postings/:id/edit" element={<PostingEditorPage />} />
          <Route path="/my/postings" element={<MyPostingsPage />} />
          <Route path="/my/offers" element={<MyOffersPage />} />
          <Route path="/messages" element={<MessagesPage />} />
          <Route path="/messages/:conversationId" element={<MessagesPage />} />
          <Route path="/deals" element={<DealsPage />} />
          <Route path="/deals/:id" element={<DealDetailPage />} />
          <Route path="/users/:id" element={<ProfilePage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="/settings/connections/callback" element={<SocialCallbackPage />} />
          <Route
            path="/admin/reports"
            element={
              <RequireAdmin>
                <AdminReportsPage />
              </RequireAdmin>
            }
          />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </Suspense>
  );
}
