import { useMutation } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { Button } from '../components/Button';
import { Field, Input } from '../components/Field';
import { Notice } from '../components/Notice';
import { fieldErrors, formError } from '../lib/errors';
import styles from './AuthPages.module.css';

type AccountType = 'MARKETER' | 'CUSTOMER';

const ACCOUNT_TYPES: { value: AccountType; title: string; text: string }[] = [
  { value: 'MARKETER', title: 'I have an audience', text: 'Streamer, creator or influencer offering promotion.' },
  { value: 'CUSTOMER', title: 'I want to advertise', text: 'A brand or business looking for marketers.' },
];

export function SignupPage() {
  const { signup } = useAuth();
  const navigate = useNavigate();
  const [userType, setUserType] = useState<AccountType>('MARKETER');
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const mutation = useMutation({
    mutationFn: () => signup({ fullName, email, phone: phone || undefined, userType, password }),
    onSuccess: () => navigate('/postings', { replace: true }),
  });
  const errors = fieldErrors(mutation.error);
  const message = formError(mutation.error);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    mutation.mutate();
  };

  return (
    <div className={`${styles.panel} ${styles.wide}`}>
      <div className={styles.card}>
        <h1 className={styles.title}>Create your account</h1>
        <p className={styles.subtitle}>Connect audiences with the brands that need them.</p>
        <form className={styles.form} onSubmit={submit} noValidate>
          {message && <Notice tone="error">{message}</Notice>}
          <div>
            <p className={styles.legend} id="account-type">
              Account type
            </p>
            <div className={styles.choices} role="radiogroup" aria-labelledby="account-type">
              {ACCOUNT_TYPES.map((type) => (
                <button
                  key={type.value}
                  type="button"
                  role="radio"
                  aria-checked={userType === type.value}
                  className={styles.choice}
                  onClick={() => setUserType(type.value)}
                >
                  <span className={styles.choiceTitle}>{type.title}</span>
                  <span className={styles.choiceText}>{type.text}</span>
                </button>
              ))}
            </div>
          </div>
          <Field label="Full name" error={errors.fullName}>
            {(props) => (
              <Input {...props} autoComplete="name" value={fullName} onChange={(e) => setFullName(e.target.value)} />
            )}
          </Field>
          <Field label="Email" error={errors.email}>
            {(props) => (
              <Input
                {...props}
                type="email"
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            )}
          </Field>
          <Field label="Phone" optional error={errors.phone}>
            {(props) => (
              <Input
                {...props}
                type="tel"
                autoComplete="tel"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
              />
            )}
          </Field>
          <Field label="Password" error={errors.password} hint="At least 8 characters, with a letter and a number.">
            {(props) => (
              <Input
                {...props}
                type="password"
                autoComplete="new-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            )}
          </Field>
          <Button type="submit" variant="primary" block loading={mutation.isPending}>
            Create account
          </Button>
        </form>
      </div>
      <p className={styles.footer}>
        Already have an account? <Link to="/login">Log in</Link>
      </p>
    </div>
  );
}
