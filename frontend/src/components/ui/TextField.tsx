import React from 'react';

interface TextFieldProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label: string;
}

const TextField: React.FC<TextFieldProps> = ({ label, id, className = '', ...props }) => {
  const inputId = id ?? props.name ?? label;

  return (
    <div>
      <label htmlFor={inputId} className="kicker mb-1.5 block">
        {label}
      </label>
      <input id={inputId} className={`field ${className}`} {...props} />
    </div>
  );
};

export default TextField;
