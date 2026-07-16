module.exports = {
  root: true,
  parser: "@typescript-eslint/parser",
  parserOptions: {
    ecmaVersion: 2022,
    sourceType: "module",
  },
  plugins: ["@typescript-eslint"],
  extends: ["eslint:recommended", "plugin:@typescript-eslint/recommended"],
  env: {
    node: true,
    es2022: true,
  },
  rules: {
    // Page Object 雛形は not-implemented を throw する前提で引数を使わないメソッドが多い。
    "@typescript-eslint/no-unused-vars": ["warn", { args: "none" }],
    "no-console": "off",
  },
  ignorePatterns: ["dist/", "node_modules/", "*.config.ts", "*.config.js"],
};
