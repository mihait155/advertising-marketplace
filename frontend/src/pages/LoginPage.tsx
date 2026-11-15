import { useMutation } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { Button } from '../components/Button';
import { Field, Input } from '../components/Field';
import { Notice } from '../components/Notice';
import { fieldErrors, formError } from '../lib/errors';
import styles from './AuthPages.module.css';

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const mutation = useMutation({
    mutationFn: () => login(email, password),
    onSuccess: () => {
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from && from !== '/login' ? from : '/postings', { replace: true });
    },
  });
  const errors = fieldErrors(mutation.error);
  const message = formError(mutation.error);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    mutation.mutate();
  };

  return (
    <div className={styles.panel}>
      <div className={styles.card}>
        <h1 className={styles.title}>Log in</h1>
        <p className={styles.subtitle}>Welcome back. Pick up where you left off.</p>
        <form className={styles.form} onSubmit={submit} noValidate>
          {message && <Notice tone="error">{message}</Notice>}
          <Field label="Email" error={errors.email}>
            {(props) => (
              <Input
                {...props}
                type="email"
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                autoFocus
              />
            )}
          </Field>
          <Field label="Password" error={errors.password}>
            {(props) => (
              <Input
                {...props}
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            )}
          </Field>
          <Button type="submit" variant="primary" block loading={mutation.isPending}>
            Log in
          </Button>
        </form>
      </div>
      <p className={styles.footer}>
        New here? <Link to="/signup">Create an account</Link>
      </p>
    </div>
  );
}
